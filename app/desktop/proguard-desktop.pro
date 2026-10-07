-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }

-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
    void sourceInformation(androidx.compose.runtime.Composer,java.lang.String);
    void sourceInformationMarkerStart(androidx.compose.runtime.Composer,int,java.lang.String);
    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
    boolean isTraceInProgress();
    void traceEventStart(int, java.lang.String);
    void traceEventEnd();
}

                          
                                                                                                                                 

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

                                                          
-dontwarn android.annotation.SuppressLint

                                                     
-dontnote kotlin.coroutines.jvm.internal.**
-dontnote kotlin.internal.**
-dontnote kotlin.jvm.internal.**
-dontnote kotlin.reflect.**
-dontnote kotlinx.coroutines.debug.internal.**
-dontnote kotlinx.coroutines.internal.**
-keep class kotlin.coroutines.Continuation
-keep class kotlinx.coroutines.CancellableContinuation
-keep class kotlinx.coroutines.channels.Channel
-keep class kotlinx.coroutines.CoroutineDispatcher
-keep class kotlinx.coroutines.CoroutineScope
                                                                                                                   
-dontwarn org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules_MatchStatementSet*

-dontnote com.sun.javafx.**

-keep class io.ktor.serialization.** { *; }
-keep class org.slf4j.** { *; }
-keep class org.slf4j2.** { *; }
-keep class * implements com.github.panpf.sketch.util.ComponentProvider { *; }
-keep class org.apache.logging.log4j.** { *; }                                                               

-keep class kotlinx.coroutines.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class androidx.datastore.** { *; }

-keep class com.sun.jna.** { *; }                 
-keep class ** implements com.sun.jna.Callback { *; }                                                                                                        

-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }                                                                                  
-keep class org.openani.mediamp.mpv.** { *; }                                                                     
                                                                                      
                                                                                   
                                                                                     
                                                               
-keep class org.openani.mediamp.io.SeekableInput { *; }

-keep class ** extends com.wynime.datasources.api.subject.SubjectProvider { *; }
-keep class ** extends com.wynime.datasources.api.source.MediaSource { *; }
-keep class ** extends com.wynime.datasources.api.source.MediaSourceFactory { *; }
-keep class ** extends io.ktor.client.HttpClientEngineContainer { *; }

                 

-keep class com.wynime.datasources.** { *; }                     
-keep class org.apache.logging.slf4j.SLF4JServiceProvider { *; }
-keep class ** extends org.slf4j.spi.SLF4JServiceProvider { *; }
-keep class org.freedesktop.dbus.** { *; }                                                        

              

-keep class io.ktor.** { *; } 
-keep class kotlin.reflect.jvm.internal.** { *; } 


                                    
-dontwarn aQute.bnd.**
-dontwarn okhttp3.internal.**
-dontwarn org.apache.logging.log4j.**	
-dontwarn reactor.blockhound.**
-dontwarn com.ctc.wstx.**
-dontwarn com.lmax.disruptor.**
-dontwarn com.sun.jna.internal.**
-dontwarn **

-dontnote **                                             

-keep class com.wynime.app.data.persistent.database.WynimeDatabase_Impl
-keep class androidx.compose.runtime.SnapshotStateKt__DerivedStateKt { *; }              
-keep class okio.Okio__JvmOkioKt { *; }              
-keep class okio.Okio__OkioKt { *; }              
-keep class okio.**              
-keep class kotlinx.serialization.json.** { *; }                                                                           

-keep class kotlin.Metadata { *; }
-keepattributes Kotlin
-keepattributes Annotation
-keepattributes RuntimeVisibleAnnotations

-keep @kotlinx.serialization.Serializable class * {*;}                                                                              

-keep class ** implements org.openani.mediamp.MediampPlayerFactory { *; }
                                                                                       
                                                                                      
                                         
-keep interface org.openani.mediamp.MediampPlayerFactory
-keep interface org.openani.mediamp.compose.MediampPlayerSurfaceProvider
-keep class ** implements org.openani.mediamp.compose.MediampPlayerSurfaceProvider { *; }

-keep class ** extends com.sun.jna.Structure { *; }               
-keep class ** extends com.sun.jna.Library { *; }      

-keep enum com.sun.jna.** { *; }                                                                  
-keep class com.jthemedetecor.** { *; }                      
-keep class oshi.** { *; }                      

                                        
-keep class androidx.compose.ui.awt.ComposeWindow { *; }
-keep class androidx.compose.ui.awt.ComposePanel { *; }
-keep class androidx.compose.ui.scene.ComposeContainer { *; }
-keep class androidx.compose.ui.scene.ComposeSceneMediator { *; }
-keep interface androidx.compose.ui.scene.ComposeScene { *; }

                                   
           
-keep class androidx.compose.foundation.HoverableNode { *; }
-keep class androidx.compose.foundation.gestures.ScrollableNode { *; }

-keep class androidx.compose.ui.scene.PlatformLayersComposeSceneImpl { *; }
-keep class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl { *; }
-keep class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl$AttachedComposeSceneLayer { *; }

               
-keepclassmembers class androidx.compose.ui.scene.PlatformLayersComposeSceneImpl {
    private *** getMainOwner();           
}

-keepclassmembers class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl {
    private *** mainOwner;                
    private *** _layersCopyCache;
    private *** focusedLayer;
}

-keepclassmembers class androidx.compose.ui.scene.CanvasLayersComposeSceneImpl$AttachedComposeSceneLayer {
    private *** owner;                    
    private *** isInBounds(...);          
}

                                         
-keep class net.bytebuddy.agent.VirtualMachine$ForHotSpot { *; }
-keep class net.bytebuddy.** { *; }

-verbose

                    
                                                                                                                                       
-keep class androidx.sqlite.driver.bundled.** { *; }
