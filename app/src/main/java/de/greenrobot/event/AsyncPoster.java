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


/**
 * Posts events in background.
 * 
 * @author Markus
 */
class AsyncPoster implements Runnable {

    private final PendingPostQueue queue;
    private final EventBus eventBus;

    AsyncPoster(EventBus eventBus) {
        this.eventBus = eventBus;
        queue = new PendingPostQueue();
    }

    /**
     * 根据参数创建待发送对象并加入队列,如果入队成功,则发送一条空消息让handleMessage响应
     *
     * @param subscription 订阅者
     * @param event        订阅事件
     */
    public void enqueue(Subscription subscription, Object event) {
        // 从复用池取出要发送的封装对象
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        // 入队
        queue.enqueue(pendingPost);
        // 获得线程池执行器，并执行
        eventBus.getExecutorService().execute(this);
    }

    @Override
    public void run() {
        // 从队列中取出对象
        PendingPost pendingPost = queue.poll();
        if(pendingPost == null) {
            throw new IllegalStateException("No pending post available");
        }
        // 如果订阅者没有取消注册,则分发消息
        eventBus.invokeSubscriber(pendingPost);
    }

}
