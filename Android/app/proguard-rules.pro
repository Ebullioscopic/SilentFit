# kotlinx.serialization keeps generated serializers through its consumer rules.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Notification listener and widget receivers are instantiated by the system.
-keep class fit.silent.app.phone.NotificationRelay { *; }
-keep class fit.silent.app.widget.** extends android.appwidget.AppWidgetProvider { *; }
