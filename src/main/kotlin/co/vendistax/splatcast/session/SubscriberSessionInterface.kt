package co.vendistax.splatcast.session

// Don't block in the implementation of this interface
interface SubscriberSessionInterface {
    fun start()
    fun stop()
}