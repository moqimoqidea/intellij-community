val x = $$$""" "Alarm Pool" prio=0 tid=0x0 nid=0x0 waiting on condition
     java.lang.Thread.State: WAITING
 on com.intellij.openapi.application.impl.RunSuspend@e1729c4
    at java.base@21.0.3/java.lang.Object.wait0(Native Method)
    at java.base@21.0.3/java.lang.Object.wait(Object.java:366)
    at java.base@21.0.3/java.lang.Object.wait(Object.java:339)
    at com.intellij.openapi.application.impl.RunSuspend.await(RunSuspend.kt:32)
    at com.intellij.openapi.application.impl.RunSuspendKt.runSuspend(RunSuspend.kt:14)
    at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getReadPermit(AnyThreadWriteThreadingSupport.kt:626)
    at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runReadAction(AnyThreadWriteThreadingSupport.kt:244)
    at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runReadAction(AnyThreadWriteThreadingSupport.kt:217)
    at com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:841)
    at com.intellij.openapi.application.impl.NonBlockingReadActionImpl$Submission.<init>(NonBlockingReadActionImpl.java:278)
    at com.intellij.openapi.application.impl.NonBlockingReadActionImpl.submit(NonBlockingReadActionImpl.java:231)
    at com.intellij.lang.javascript.modules.remote.JSRemoteModulesUsagesDetector.performNonBlockingUpdate(JSRemoteModulesUsagesDetector.java:108)
    at com.intellij.lang.javascript.modules.remote.JSRemoteModulesUsagesDetector$$Lambda/0x0000000101cb77a8.run(Unknown Source)
    at com.intellij.util.Alarm$Request.lambda$runSafely$0(Alarm.java:371)
    at com.intellij.util.Alarm$Request$$Lambda/0x0000000100fe5200.run(Unknown Source)
    at com.intellij.util.concurrency.ChildContext$runAsCoroutine$1.invoke(propagation.kt:89)
    at com.intellij.util.concurrency.ChildContext$runAsCoroutine$1.invoke(propagation.kt:89) """
