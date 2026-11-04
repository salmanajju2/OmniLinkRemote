# Google Play Store पर लाइव करने की पूरी गाइड (Hindi Step-by-Step Guide)

इस गाइड में बताया गया है कि **OmniLink Remote** (All-in-One Remote Desktop) ऐप को बिना किसी रिजेक्शन के Google Play Store पर सफलतापूर्वक कैसे पब्लिश (Live) करें।

---

## 1. Google Play Console अकाउंट सेटअप

1. **वेबसाइट:** [play.google.com/console](https://play.google.com/console/) पर जाएँ।
2. **फीस:** एक बार की फ़ीस **\$25 (लगभग ₹2,200)** अपने इंटरनेशनल डेबिट/क्रेडिट कार्ड से भरें।
3. **पहचान सत्यापन (Identity Verification):**
   * अपना सरकारी पहचान पत्र (PAN कार्ड / पासपोर्ट / ड्राइविंग लाइसेंस) अपलोड करें।
   * पते का प्रमाण (Address Proof) अपलोड करें।
4. **20 टेस्टर्स नियम (New Personal Accounts के लिए):**
   * गूगल के नए नियम के अनुसार, ऐप को पब्लिक करने से पहले **14 दिनों के लिए कम से कम 20 टेस्टर्स** के साथ Closed Testing चलानी होती है। (इसके लिए आप दोस्तों या टेस्टिंग ग्रुप्स का उपयोग कर सकते हैं)।

---

## 2. ऐप का Release Bundle (.aab) तैयार करना

1. **Android Studio में प्रोजेक्ट खोलें:**
   * Android Studio खोलें -> `Open` पर क्लिक करें -> `web testing/android` फ़ोल्डर चुनें।
   * Gradle Sync पूरा होने दें।
2. **रिलीज़ की-स्टोर (Keystore) बनाएँ:**
   * मेनू में जाएँ: `Build` -> `Generate Signed Bundle / APK...`
   * **Android App Bundle (.aab)** चुनें -> Next।
   * `Create new...` पर क्लिक करें और पासवर्ड व अपनी जानकारी भरें।
   * **महत्वपूर्ण:** इस Keystore फ़ाइल और पासवर्ड को सुरक्षित रखें, भविष्य के अपडेट के लिए यह अनिवार्य है।
3. **Build Release AAB:**
   * `release` वैरिएंट चुनें और `Finish` पर क्लिक करें।
   * फ़ाइल `android/app/release/app-release.aab` में बन जाएगी।

---

## 3. सबसे महत्वपूर्ण: Google Play Store नीतियां (Rejection से बचने के उपाय)

TeamViewer जैसे रिमोट कंट्रोल ऐप्स में **Accessibility Service** और **MediaProjection** का इस्तेमाल होता है। गूगल इसके लिए 2 चीज़ें अनिवार्य रूप से मांगता है:

### A. Accessibility Declaration Form (गूगल फॉर्म में क्या भरें?)
Play Console में जब आपसे Accessibility Service के बारे में पूछा जाए, तो यह उत्तर भरें:

> **Why does your app use Accessibility Services?**
> *"OmniLink Remote uses the AccessibilityService API solely for user-authorized remote support and control sessions. When a remote session is initiated by the device owner, the service simulates touch events (taps, drags, swipes) and navigation commands (Back, Home, Recents) on behalf of the remote operator. The app does not collect, log, or transmit any sensitive user data, passwords, or keystrokes."*

### B. YouTube Demo Video (अनिवार्य)
गूगल समीक्षक (Reviewer) को यह दिखाना होता है कि ऐप में एक्सेसिबिलिटी कैसे काम करती है:
1. अपने फोन में स्क्रीन रिकॉर्डर चालू करें।
2. ऐप खोलें -> "शेयर स्क्रीन" में जाएँ -> **"एक्सेसिबिलिटी सर्विस प्रकटीकरण" (Disclosure Dialog)** दिखाएँ।
3. सेटिंग्स में जाकर सर्विस चालू करें।
4. किसी दूसरे कंप्यूटर/फोन से कनेक्ट करके दिखाएँ कि स्क्रीन पर रिमोट क्लिक हो रहा है।
5. इस 30-40 सेकंड के वीडियो को YouTube पर **Unlisted** अपलोड करें और इसका लिंक Play Console में पेस्ट करें।

---

## 4. निजता नीति (Privacy Policy Template)

गूगल प्ले स्टोर पर ऐप सबमिट करते समय एक लाइव Privacy Policy URL देना ज़रूरी है। आप [Google Sites](https://sites.google.com) या GitHub Pages पर एक मुफ़्त पेज बनाकर नीचे दिया गया टेक्स्ट डाल सकते हैं:

```text
Privacy Policy for OmniLink Remote

Last updated: November 2026

1. Introduction:
OmniLink Remote provides real-time screen sharing and remote assistance between user-authorized devices.

2. MediaProjection & Screen Capture:
The app requests MediaProjection permission solely to stream the device screen to an authorized peer session. Screen streaming only occurs while an explicit ongoing foreground service notification is displayed.

3. AccessibilityService API:
OmniLink Remote utilizes Android's AccessibilityService API solely to perform touch gestures (tap, drag, swipe) and navigation keys (Home, Back, Recents) during an active remote control session approved by the user. 
- We DO NOT read screen text, passwords, financial data, or keystrokes.
- We DO NOT store or monetize any personal data.

4. Data Transfer:
All peer-to-peer audio, video, and file transfers are directly established via encrypted WebRTC channels.

5. Contact Us:
For queries, contact: support@omnilinkremote.com
```

---

## 5. इन-ऐप परचेज (Rate से Account कैसे लगेगा)

1. **Google Play Billing Setup:**
   * Play Console -> `Monetize with Play` -> `In-app products` या `Subscriptions` में जाएँ।
   * अपना प्लान बनाएं (जैसे: ₹99/महीना या ₹499/साल)।
   * गूगल पेआउट के लिए अपना भारतीय बैंक खाता (Bank Account & IFSC) जोड़ें।
2. गूगल हर इन-ऐप ख़रीद पर 15% शुल्क काटेगा और बाकी राशि हर महीने की 15 तारीख को सीधे आपके बैंक खाते में जमा कर देगा।
