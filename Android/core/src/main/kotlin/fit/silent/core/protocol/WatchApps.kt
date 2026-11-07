package fit.silent.core.protocol

/**
 * How the NF 2 picks a notification's icon and app name.
 *
 * The firmware keys on SEAppNotification.app_name, which NoiseFit fills with a fixed type tag ("whats app",
 * "gmail", …), not the app's label, and page_name with a canonical package. Anything else shows as "Other".
 * Table from NoiseFit 5.5.0 (AppStaticData.N + DataConverter.O); see docs/PROTOCOL.md §5.
 */
object WatchApps {
    /** (tag sent as app_name, package sent as page_name) for apps the watch has an icon for. */
    data class Target(val tag: String, val pageName: String)

    val OTHER = Target("other", "")

    private val BY_PACKAGE: Map<String, Target> = listOf(
        "com.whatsapp.w4b" to "whatsapp_business",
        "com.whatsapp" to "whats_app",
        "org.telegram.messenger" to "telegram",
        "com.snapchat.android" to "snapchat",
        "com.Slack" to "slack",
        "com.google.android.youtube" to "youtube",
        "com.microsoft.office.outlook" to "outlook",
        "com.instagram.android" to "instagram",
        "com.linkedin.android" to "linked_in",
        "com.google.android.gm" to "gmail",
        "com.viber.voip" to "viber",
        "com.facebook.katana" to "facebook",
        "com.facebook.orca" to "fb_messenger",
        "com.skype.raider" to "skype",
        "com.twitter.android" to "twitter",
        "com.tencent.mm" to "wechat",
        "com.google.android.apps.maps" to "google_maps",
        "com.google.android.calendar" to "calendar",
        "com.pinterest" to "pintrest",
        "in.amazon.mShop.android.shopping" to "amazon",
        "com.flipkart.android" to "flipkart",
        "com.google.android.apps.magazines" to "google_news",
        "com.google.android.apps.nbu.paisa.user" to "google_pay",
        "com.google.android.talk" to "hangouts",
        "com.olacabs.customer" to "ola",
        "net.one97.paytm" to "paytm",
        "com.phonepe.app" to "phone_pe",
        "com.ubercab" to "uber",
        "naukriApp.appModules.login" to "naukri",
        "com.nis.app" to "inshorts",
        "com.noisefit" to "noisefit",
        "com.tencent.mobileqq" to "qq",
        "com.sina.weibo" to "sina_weibo",
        "com.eg.android.AlipayGphone" to "alipay",
        "com.viber" to "rakuten_viber_messenger",
        "com.kakao.talk" to "kaokao",
        "ru.ok.android" to "ok",
        "jp.naver.line.android" to "line",
        "com.myairtelapp" to "airtel",
        "com.tvsm.connect" to "tvs_connect",
        "com.google.android.apps.classroom" to "google_classroom",
    ).associate { (pkg, type) -> pkg to Target(type.replace('_', ' '), pkg) }

    fun forPackage(packageName: String): Target = BY_PACKAGE[packageName] ?: OTHER

    fun hasIcon(packageName: String) = packageName in BY_PACKAGE
}
