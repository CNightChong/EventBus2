package de.greenrobot.event;

/**
 * 封装的发送对象组成的队列
 */
final class PendingPostQueue {

    private PendingPost head; // 待发送对象队列头节点
    private PendingPost tail;// 待发送对象队列尾节点

    /**
     * 入队
     */
    synchronized void enqueue(PendingPost pendingPost) {
        if (pendingPost == null) {
            throw new NullPointerException("null cannot be enqueued");
        }
        if (tail != null) {
            // 入队前整个队列的最后一个节点的尾指针指向当前正在入队的节点
            tail.next = pendingPost;
            // 正在入队的节点变成队列的最后一个节点
            tail = pendingPost;
        } else if (head == null) {
            // 如果队列之前是空的,那么直接将队列的头尾两个指针都指向自身
            head = tail = pendingPost;
        } else {
            throw new IllegalStateException("Head present, but no tail");
        }
        notifyAll();
    }

    /**
     * 取队列头节点的待发送对象
     */
    synchronized PendingPost poll() {
        PendingPost pendingPost = head;
        if (head != null) {
            // 将出队前的第二个元素(出队后的第一个元素)的赋值为现在队列的头节点
            head = head.next;
            if (head == null) {
                tail = null;
            }
        }
        return pendingPost;
    }

    /**
     * 取待发送对象队列头节点的待发送对象
     */
    synchronized PendingPost poll(int maxMillisToWait) throws InterruptedException {
        if (head == null) {
            wait(maxMillisToWait);
        }
        return poll();
    }
}
