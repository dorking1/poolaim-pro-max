# PoolAim Pro Max

تطبيق Android لمساعدة لاعبي البلياردو في حساب أفضل ضربة بكرات 8-ball اعتمادًا على صورة واحدة للطاولة.

## المعمارية

- **Kotlin + Jetpack Compose (Material 3)** مع MVVM وClean Architecture
- **minSdk 26 / targetSdk 35**
- **DataStore** للإعدادات، **Room** للتاريخ
- دعم كامل للعربية (RTL) والإنجليزية

## المراحل

| المرحلة | النطاق | الحالة |
|---|---|---|
| A | هيكل المشروع، استيراد الصور، اختيار مجموعة اللاعب | 🚧 جارية |
| B | كشف حدود الطاولة (تلقائي + معايرة يدوية) | ⏳ |
| C | كشف الكرات وتصنيفها | ⏳ |
| D | الفيزياء وحساب الضربة المثلى | ⏳ |
| E | تراكب AimBoard على الصورة | ⏳ |
| F | التاريخ (Room) والتقارير | ⏳ |
| G | تحسين الأداء والاختبارات | ⏳ |
| H | الإصدار النهائي (توقيع release + ProGuard) | ⏳ |

## البناء السحابي

دفع أي commit إلى `main` أو `phase-*` يشغّل GitHub Actions الذي يبني APK تلقائيًا — حمّله من صفحة **Actions**.

## البناء المحلي

```bash
git clone https://github.com/dorking1/poolaim-pro-max.git
cd poolaim-pro-max
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

يتطلب JDK 17 وAndroid SDK (يُدار تلقائيًا عبر Android Studio).
