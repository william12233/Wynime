                                                             
                                                      
                                                

                       
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

                                                                        
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

                                                                                                   
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

                                                                                                              
                                                                                                              
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal

                                                                   
                                                                                                 
-dontwarn java.lang.ClassValue

                                                                   
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
