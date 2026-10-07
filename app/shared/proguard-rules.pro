-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault


                     
-keep class org.openapitools.** {
	*;
}

                                                                                 
-keep public class com.wynime.source.plugin.api.** {
    *;
}
-keep public interface com.wynime.source.plugin.api.** {
    *;
}

                                                                             
                                                                       
                                                                      
-keep class kotlin.** {
    *;
}

-keep class ** extends com.wynime.datasources.api.subject.SubjectProvider {}
-keep class ** extends com.wynime.datasources.api.source.MediaSource {}
-keep class ** extends com.wynime.datasources.api.source.MediaSourceFactory {}

                                                                  
-keepclassmembers class androidx.media3.exoplayer.ExoPlayerImpl {
    androidx.media3.exoplayer.upstream.BandwidthMeter bandwidthMeter;
}
-keepclassmembers class androidx.media3.exoplayer.mediacodec.MediaCodecRenderer {
    androidx.media3.exoplayer.mediacodec.MediaCodecInfo codecInfo;
}

-keep class com.wynime.app.ui.settings.tabs.** {*;}                            
-keep class com.wynime.app.navigation.** {*;}                    
-keep class com.wynime.app.ui.subject.cache.** {*;}                  


                 
-keepclassmembers class ch.qos.logback.classic.pattern.* { <init>(); }
                                                             
                                         
-keepclassmembers class ch.qos.logback.** { *; }                                                                                                                                  
-keepclassmembers class org.slf4j.impl.** { *; }
                                       
-keep class ch.qos.logback.classic.android.LogcatAppender
-keep class ch.qos.logback.core.rolling.RollingFileAppender
-keep class ch.qos.logback.core.rolling.TimeBasedRollingPolicy
                                                                 
-dontwarn javax.mail.**


               
-keep class org.openani.mediamp.ffmpeg.JvmFFmpegProcess { *; }

             
-keep class ai.onnxruntime.** { *; }                                                                         

-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile
-keepnames class com.wynime.** { *; }
-keepnames class ** { *; }                                                                                                           
