/*
 * Copyright (C) 2012 Markus Junginger, greenrobot (http://greenrobot.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.greenrobot.event;

import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SubscriberMethodFinder {
    private static final String ON_EVENT_METHOD_NAME = "onEvent";

    /**
     * 在较新的类文件，编译器可能会添加方法。那些被称为BRIDGE或SYNTHETIC方法。
     * EventBus必须忽略两者。有修饰符没有公开，但在Java类文件中有格式定义
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;
    // 需要忽略的修饰符
    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;

    // 整个库在运行期间所有遍历的方法都会存在这个 map 中，而不必每次都去做耗时的反射取方法了
    // 全局静态变量，key:类名,value:该类中需要相应的方法集合
    private static final Map<String, List<SubscriberMethod>> methodCache = new HashMap<>();

    // 跳过校验方法的类(即通过构造函数传入的集合)
    private final Map<Class<?>, Class<?>> skipMethodVerificationForClasses;

    /**
     * 构造方法
     *
     * @param skipMethodVerificationForClassesList 需要跳过校验方法的类
     */
    SubscriberMethodFinder(List<Class<?>> skipMethodVerificationForClassesList) {
        skipMethodVerificationForClasses = new ConcurrentHashMap<>();
        if (skipMethodVerificationForClassesList != null) {
            for (Class<?> clazz : skipMethodVerificationForClassesList) {
                skipMethodVerificationForClasses.put(clazz, clazz);
            }
        }
    }

    /**
     * 查找一个类中全部的需要响应的订阅方法
     *
     * @param subscriberClass 待查找的类
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        String key = subscriberClass.getName();
        List<SubscriberMethod> subscriberMethods;
        synchronized (methodCache) {
            subscriberMethods = methodCache.get(key);
        }
        if (subscriberMethods != null) {
            return subscriberMethods;
        }

        subscriberMethods = new ArrayList<>();
        Class<?> clazz = subscriberClass;
        HashSet<String> eventTypesFound = new HashSet<>();
        StringBuilder methodKeyBuilder = new StringBuilder();
        while (clazz != null) {
            String name = clazz.getName();
            // 不是 java编译器 生成的方法名
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android" +
                    ".")) {
                // Skip system classes, this just degrades performance
                break;
            }

            // 从2.2版本开始,响应的方法必须是public的 (might change with annotations again)
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                String methodName = method.getName();
                // 只关注以 “onEvent” 开头的方法
                if (methodName.startsWith(ON_EVENT_METHOD_NAME)) {
                    int modifiers = method.getModifiers();//方法的修饰符
                    // 如果是public,且不是之前定义要忽略的类型
                    if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        // 如果只有一个参数
                        if (parameterTypes.length == 1) {
                            // 对方法名进行截取 onEventXxxxx
                            String modifierString = methodName.substring(ON_EVENT_METHOD_NAME.length());
                            ThreadMode threadMode;
                            // 根据不同的方法名赋予不同的线程模式
                            if (modifierString.length() == 0) {
                                threadMode = ThreadMode.PostThread;
                            } else if (modifierString.equals("MainThread")) {
                                threadMode = ThreadMode.MainThread;
                            } else if (modifierString.equals("BackgroundThread")) {
                                threadMode = ThreadMode.BackgroundThread;
                            } else if (modifierString.equals("Async")) {
                                threadMode = ThreadMode.Async;
                            } else {
                                if (skipMethodVerificationForClasses.containsKey(clazz)) {
                                    continue;
                                } else {
                                    throw new EventBusException("Illegal onEvent method, check " +
                                            "for typos: " + method);
                                }
                            }
                            Class<?> eventType = parameterTypes[0];
                            methodKeyBuilder.setLength(0);
                            methodKeyBuilder.append(methodName);
                            methodKeyBuilder.append('>').append(eventType.getName());
                            String methodKey = methodKeyBuilder.toString();
                            if (eventTypesFound.add(methodKey)) {
                                // 方法名,工作在哪个线程,事件类型
                                subscriberMethods.add(new SubscriberMethod(method, threadMode,
                                        eventType));
                            }
                        }
                    } else if (!skipMethodVerificationForClasses.containsKey(clazz)) {
                        Log.d(EventBus.TAG, "Skipping method (not public, static or abstract): "
                                + clazz + "." + methodName);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass + " has no public methods" +
                    " called " + ON_EVENT_METHOD_NAME);
        } else {
            synchronized (methodCache) {
                // value:订阅者类名，key：封装订阅者方法对象列表
                methodCache.put(key, subscriberMethods);
            }
            return subscriberMethods;
        }
    }

    static void clearCaches() {
        synchronized (methodCache) {
            methodCache.clear();
        }
    }
}
