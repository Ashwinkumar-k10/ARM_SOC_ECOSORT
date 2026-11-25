package com.ecosort.edge.ui

import com.ecosort.edge.domain.model.AppLanguage
import com.ecosort.edge.domain.model.StreamType

data class AppStrings(
    val navHome: String,
    val navScan: String,
    val navAssistant: String,
    val appSubtitle: String,
    val offlineAi: String,
    val telemetryTitle: String,
    val visionLabel: String,
    val llamaLabel: String,
    val ready: String,
    val error: String,
    val importRequired: String,
    val impactTitle: String,
    val airGapped: String,
    val totalScans: String,
    val sortedClean: String,
    val hazardsCaught: String,
    val scanWasteItem: String,
    val sanitaryPath: String,
    val cameraFreePrivacy: String,
    val ecoAssistant: String,
    val recentActivity: String,
    val noScansYet: String,
    val cameraPermNeeded: String,
    val grantCameraPerm: String,
    val runningExecuTorch: String,
    val alignWasteItem: String,
    val testPrefix: String,
    val sampleBattery: String,
    val samplePlastic: String,
    val sampleFood: String,
    val sampleCardboard: String,
    val noItemSelected: String,
    val sortingResult: String,
    val confidenceLabel: String,
    val reticleCrop: String,
    val statusLabel: String,
    val sanitaryPrivacyMsg: String,
    val fireHazardBanner: String,
    val llamaStepsTitle: String,
    val llamaNotImported: String,
    val generatingSteps: String,
    val statutoryLabelsTitle: String,
    val askFollowUp: String,
    val importLlamaBtn: String,
    val llamaReadyBadge: String,
    val modelNeededBadge: String,
    val scanFirstHint: String,
    val importCardTitle: String,
    val copyingVerifying: String,
    val selectModelFile: String,
    val thinkingOnDevice: String,
    val promptPrep: String,
    val promptWhyBin: String,
    val promptImpact: String,
    val askPlaceholder: String
)

object AppLocalization {

    private val en = AppStrings(
        navHome = "Home",
        navScan = "Scan",
        navAssistant = "Assistant",
        appSubtitle = "SWM 2026 On-Device Segregation",
        offlineAi = "Offline AI",
        telemetryTitle = "Arm SoC Telemetry (Live)",
        visionLabel = "Vision INT8",
        llamaLabel = "Llama 3.2 INT8",
        ready = "Ready (2.41 MB)",
        error = "Error",
        importRequired = "Import Required",
        impactTitle = "Household Segregation Impact",
        airGapped = "Air-Gapped",
        totalScans = "Total Scans",
        sortedClean = "Sorted Clean",
        hazardsCaught = "Hazards Caught",
        scanWasteItem = "Scan Waste Item",
        sanitaryPath = "Sanitary Path",
        cameraFreePrivacy = "Camera-free privacy",
        ecoAssistant = "Eco Assistant",
        recentActivity = "Recent Sorting Activity",
        noScansYet = "No items scanned yet. Tap 'Scan Waste Item' to classify with ExecuTorch INT8.",
        cameraPermNeeded = "Camera access needed for live scanning",
        grantCameraPerm = "Grant Camera Permission",
        runningExecuTorch = "Running ExecuTorch INT8...",
        alignWasteItem = "Align waste item inside frame",
        testPrefix = "Test",
        sampleBattery = "E-Waste",
        samplePlastic = "Plastic",
        sampleFood = "Food",
        sampleCardboard = "Cardboard",
        noItemSelected = "No item selected",
        sortingResult = "Sorting Result",
        confidenceLabel = "confidence",
        reticleCrop = "Reticle Focus Crop",
        statusLabel = "Status",
        sanitaryPrivacyMsg = "Camera-Free Sanitary Privacy Mode Active. Wrap securely before disposal.",
        fireHazardBanner = "Fire-Hazard Interlock: Keep dry, tape terminals, and place only in the Red Bin.",
        llamaStepsTitle = "On-Device Llama 3.2 Steps",
        llamaNotImported = "Llama 3.2 1B INT8 not imported yet. Tap below to import it once.",
        generatingSteps = "Generating safe preparation steps...",
        statutoryLabelsTitle = "Statutory Bin Labels (SWM 2026)",
        askFollowUp = "Ask Follow-Up Question",
        importLlamaBtn = "Import Llama 3.2 INT8",
        llamaReadyBadge = "Llama INT8 Ready",
        modelNeededBadge = "Model Needed",
        scanFirstHint = "Scan an item first to ground Llama 3.2 1B INT8.",
        importCardTitle = "Import On-Device Llama 3.2 1B INT8",
        copyingVerifying = "Copying & Verifying...",
        selectModelFile = "Select llama3_2_1b_int8.pte",
        thinkingOnDevice = "Thinking on-device...",
        promptPrep = "How do I prep this?",
        promptWhyBin = "Why this bin?",
        promptImpact = "Environmental impact?",
        askPlaceholder = "Ask about recycling or disposal..."
    )

    private val ta = AppStrings(
        navHome = "முகப்பு",
        navScan = "ஸ்கேன்",
        navAssistant = "உதவியாளர்",
        appSubtitle = "SWM 2026 சாதனப் பிரிப்பு",
        offlineAi = "ஆஃப்லைன் AI",
        telemetryTitle = "Arm SoC நிலை (நேரலை)",
        visionLabel = "பார்வை INT8",
        llamaLabel = "Llama 3.2 INT8",
        ready = "தயார் (2.41 MB)",
        error = "பிழை",
        importRequired = "இறக்குமதி தேவை",
        impactTitle = "வீட்டுக் கழிவு பிரிப்பு தாக்கம்",
        airGapped = "ஆஃப்லைன்",
        totalScans = "மொத்த ஸ்கேன்",
        sortedClean = "சுத்தமாக பிரிப்பு",
        hazardsCaught = "அபாயம் தடுப்பு",
        scanWasteItem = "கழிவை ஸ்கேன் செய்",
        sanitaryPath = "சுகாதாரப் பிரிவு",
        cameraFreePrivacy = "கேமரா இல்லா தனியுரிமை",
        ecoAssistant = "சூழல் உதவியாளர்",
        recentActivity = "சமீபத்திய பிரிப்பு",
        noScansYet = "இன்னும் ஸ்கேன் இல்லை. ExecuTorch INT8 மூலம் பிரிக்க 'கழிவை ஸ்கேன் செய்' என்பதைத் தட்டவும்.",
        cameraPermNeeded = "நேரடி ஸ்கேனுக்கு கேமரா அனுமதி தேவை",
        grantCameraPerm = "கேமரா அனுமதி வழங்கு",
        runningExecuTorch = "ExecuTorch INT8 இயங்குகிறது...",
        alignWasteItem = "பொருளை சட்டத்திற்குள் வைக்கவும்",
        testPrefix = "சோதனை",
        sampleBattery = "மின்னணு",
        samplePlastic = "பிளாஸ்டிக்",
        sampleFood = "உணவு",
        sampleCardboard = "அட்டை",
        noItemSelected = "பொருள் தேர்ந்தெடுக்கப்படவில்லை",
        sortingResult = "பிரிப்பு முடிவு",
        confidenceLabel = "நம்பகம்",
        reticleCrop = "குவிக்கப்பட்ட படம்",
        statusLabel = "நிலை",
        sanitaryPrivacyMsg = "கேமரா இல்லா சுகாதார தனியுரிமை பயன்முறை. பாதுகாப்பாக மடித்து அகற்றவும்.",
        fireHazardBanner = "தீ அபாய எச்சரிக்கை: உலர்ந்ததாக வைத்து, முனைகளை டேப் ஒட்டி சிவப்புத் தொட்டியில் மட்டும் போடவும்.",
        llamaStepsTitle = "சாதன Llama 3.2 படிகள்",
        llamaNotImported = "Llama 3.2 1B INT8 இன்னும் சேர்க்கப்படவில்லை. சேர்க்க கீழே தட்டவும்.",
        generatingSteps = "பாதுகாப்பான படிகளை உருவாக்குகிறது...",
        statutoryLabelsTitle = "சட்டபூர்வ தொட்டி லேபிள்கள் (SWM 2026)",
        askFollowUp = "மேலும் கேள்வி கேட்க",
        importLlamaBtn = "Llama 3.2 INT8 சேர்க்க",
        llamaReadyBadge = "Llama INT8 தயார்",
        modelNeededBadge = "மாடல் தேவை",
        scanFirstHint = "Llama 3.2 1B INT8 பயன்படுத்த முதலில் ஒரு பொருளை ஸ்கேன் செய்யவும்.",
        importCardTitle = "சாதனத்தில் Llama 3.2 1B INT8 சேர்க்கவும்",
        copyingVerifying = "நகலெடுத்து சரிபார்க்கிறது...",
        selectModelFile = "llama3_2_1b_int8.pte தேர்வு செய்",
        thinkingOnDevice = "சாதனத்தில் சிந்திக்கிறது...",
        promptPrep = "இதை எப்படி தயார் செய்வது?",
        promptWhyBin = "ஏன் இந்தத் தொட்டி?",
        promptImpact = "சுற்றுச்சூழல் தாக்கம்?",
        askPlaceholder = "மறுசுழற்சி பற்றி கேட்கவும்..."
    )

    private val kn = AppStrings(
        navHome = "ಮುಖಪುಟ",
        navScan = "ಸ್ಕ್ಯಾನ್",
        navAssistant = "ಸಹಾಯಕ",
        appSubtitle = "SWM 2026 ಸಾಧನದಲ್ಲೇ ವಿಂಗಡಣೆ",
        offlineAi = "ಆಫ್‌ಲೈನ್ AI",
        telemetryTitle = "Arm SoC ಸ್ಥಿತಿ (ಲೈವ್)",
        visionLabel = "ವಿಷನ್ INT8",
        llamaLabel = "Llama 3.2 INT8",
        ready = "ಸಿದ್ಧ (2.41 MB)",
        error = "ದೋಷ",
        importRequired = "ಆಮದು ಅಗತ್ಯವಿದೆ",
        impactTitle = "ಮನೆಯ ತ್ಯಾಜ್ಯ ವಿಂಗಡಣೆ ಪರಿಣಾಮ",
        airGapped = "ಆಫ್‌ಲೈನ್",
        totalScans = "ಒಟ್ಟು ಸ್ಕ್ಯಾನ್",
        sortedClean = "ಸ್ವಚ್ಛ ವಿಂಗಡಣೆ",
        hazardsCaught = "ಅಪಾಯ ಪತ್ತೆ",
        scanWasteItem = "ತ್ಯಾಜ್ಯ ಸ್ಕ್ಯಾನ್ ಮಾಡಿ",
        sanitaryPath = "ನೈರ್ಮಲ್ಯ ಮಾರ್ಗ",
        cameraFreePrivacy = "ಕ್ಯಾಮೆರಾ-ರಹಿತ ಗೌಪ್ಯತೆ",
        ecoAssistant = "ಪರಿಸರ ಸಹಾಯಕ",
        recentActivity = "ಇತ್ತೀಚಿನ ವಿಂಗಡಣೆ",
        noScansYet = "ಇನ್ನೂ ಸ್ಕ್ಯಾನ್ ಮಾಡಿಲ್ಲ. ExecuTorch INT8 ಮೂಲಕ ವಿಂಗಡಿಸಲು 'ತ್ಯಾಜ್ಯ ಸ್ಕ್ಯಾನ್ ಮಾಡಿ' ಒತ್ತಿರಿ.",
        cameraPermNeeded = "ಲೈವ್ ಸ್ಕ್ಯಾನ್‌ಗೆ ಕ್ಯಾಮೆರಾ ಅನುಮತಿ ಬೇಕು",
        grantCameraPerm = "ಕ್ಯಾಮೆರಾ ಅನುಮತಿ ನೀಡಿ",
        runningExecuTorch = "ExecuTorch INT8 ಚಾಲನೆಯಲ್ಲಿದೆ...",
        alignWasteItem = "ವಸ್ತುವನ್ನು ಚೌಕಟ್ಟಿನೊಳಗೆ ಇರಿಸಿ",
        testPrefix = "ಪರೀಕ್ಷೆ",
        sampleBattery = "ಇ-ತ್ಯಾಜ್ಯ",
        samplePlastic = "ಪ್ಲಾಸ್ಟಿಕ್",
        sampleFood = "ಆಹಾರ",
        sampleCardboard = "ರಟ್ಟಿನ ಪೆಟ್ಟಿಗೆ",
        noItemSelected = "ಯಾವುದೇ ವಸ್ತು ಆಯ್ಕೆಯಾಗಿಲ್ಲ",
        sortingResult = "ವಿಂಗಡಣೆ ಫಲಿತಾಂಶ",
        confidenceLabel = "ವಿಶ್ವಾಸ",
        reticleCrop = "ಕೇಂದ್ರೀಕೃತ ಚಿತ್ರ",
        statusLabel = "ಸ್ಥಿತಿ",
        sanitaryPrivacyMsg = "ಕ್ಯಾಮೆರಾ-ರಹಿತ ನೈರ್ಮಲ್ಯ ಗೌಪ್ಯತೆ ಸಕ್ರಿಯವಾಗಿದೆ. ಸುರಕ್ಷಿತವಾಗಿ ಸುತ್ತಿ ವಿಲೇವಾರಿ ಮಾಡಿ.",
        fireHazardBanner = "ಬೆಂಕಿ ಅಪಾಯ ಎಚ್ಚರಿಕೆ: ಒಣಗಿಸಿಡಿ, ತುದಿಗಳಿಗೆ ಟೇಪ್ ಹಚ್ಚಿ ಕೆಂಪು ಡಬ್ಬಿಯಲ್ಲಿ ಮಾತ್ರ ಹಾಕಿ.",
        llamaStepsTitle = "ಸಾಧನದಲ್ಲೇ Llama 3.2 ಹಂತಗಳು",
        llamaNotImported = "Llama 3.2 1B INT8 ಇನ್ನೂ ಆಮದು ಮಾಡಿಲ್ಲ. ಆಮದು ಮಾಡಲು ಕೆಳಗೆ ಒತ್ತಿರಿ.",
        generatingSteps = "ಸುರಕ್ಷಿತ ಹಂತಗಳನ್ನು ರಚಿಸಲಾಗುತ್ತಿದೆ...",
        statutoryLabelsTitle = "ಶಾಸನಬದ್ಧ ಡಬ್ಬಿ ಲೇಬಲ್‌ಗಳು (SWM 2026)",
        askFollowUp = "ಮುಂದಿನ ಪ್ರಶ್ನೆ ಕೇಳಿ",
        importLlamaBtn = "Llama 3.2 INT8 ಆಮದು ಮಾಡಿ",
        llamaReadyBadge = "Llama INT8 ಸಿದ್ಧ",
        modelNeededBadge = "ಮಾಡೆಲ್ ಅಗತ್ಯವಿದೆ",
        scanFirstHint = "Llama 3.2 1B INT8 ಬಳಸಲು ಮೊದಲು ವಸ್ತುವನ್ನು ಸ್ಕ್ಯಾನ್ ಮಾಡಿ.",
        importCardTitle = "Llama 3.2 1B INT8 ಆಮದು ಮಾಡಿ",
        copyingVerifying = "ನಕಲಿಸಿ ಪರಿಶೀಲಿಸಲಾಗುತ್ತಿದೆ...",
        selectModelFile = "llama3_2_1b_int8.pte ಆಯ್ಕೆಮಾಡಿ",
        thinkingOnDevice = "ಸಾಧನದಲ್ಲೇ ಯೋಚಿಸುತ್ತಿದೆ...",
        promptPrep = "ಇದನ್ನು ಹೇಗೆ ಸಿದ್ಧಪಡಿಸುವುದು?",
        promptWhyBin = "ಈ ಡಬ್ಬಿ ಏಕೆ?",
        promptImpact = "ಪರಿಸರ ಪರಿಣಾಮ?",
        askPlaceholder = "ಮರುಬಳಕೆ ಬಗ್ಗೆ ಕೇಳಿ..."
    )

    private val hi = AppStrings(
        navHome = "होम",
        navScan = "स्कैन",
        navAssistant = "सहायक",
        appSubtitle = "SWM 2026 ऑन-डिवाइस पृथक्करण",
        offlineAi = "ऑफ़लाइन AI",
        telemetryTitle = "Arm SoC टेलीमेट्री (लाइव)",
        visionLabel = "विज़न INT8",
        llamaLabel = "Llama 3.2 INT8",
        ready = "तैयार (2.41 MB)",
        error = "त्रुटि",
        importRequired = "इम्पोर्ट आवश्यक",
        impactTitle = "घरेलू कचरा पृथक्करण प्रभाव",
        airGapped = "ऑफ़लाइन",
        totalScans = "कुल स्कैन",
        sortedClean = "स्वच्छ छंटाई",
        hazardsCaught = "खतरे की पहचान",
        scanWasteItem = "कचरा स्कैन करें",
        sanitaryPath = "सैनिटरी मार्ग",
        cameraFreePrivacy = "कैमरा-मुक्त गोपनीयता",
        ecoAssistant = "इको सहायक",
        recentActivity = "हाल की छंटाई",
        noScansYet = "अभी तक कोई स्कैन नहीं। ExecuTorch INT8 से पहचानने के लिए 'कचरा स्कैन करें' दबाएं।",
        cameraPermNeeded = "लाइव स्कैन के लिए कैमरा अनुमति चाहिए",
        grantCameraPerm = "कैमरा अनुमति दें",
        runningExecuTorch = "ExecuTorch INT8 चल रहा है...",
        alignWasteItem = "कचरे को फ्रेम के अंदर रखें",
        testPrefix = "टेस्ट",
        sampleBattery = "ई-कचरा",
        samplePlastic = "प्लास्टिक",
        sampleFood = "भोजन",
        sampleCardboard = "गत्ता",
        noItemSelected = "कोई वस्तु चयनित नहीं",
        sortingResult = "छंटाई परिणाम",
        confidenceLabel = "विश्वास",
        reticleCrop = "फोकस क्रॉप चित्र",
        statusLabel = "स्थिति",
        sanitaryPrivacyMsg = "कैमरा-मुक्त सैनिटरी गोपनीयता सक्रिय। निपटान से पहले सुरक्षित लपेटें।",
        fireHazardBanner = "आग का खतरा चेतावनी: सूखा रखें, टर्मिनल पर टेप लगाएं और केवल लाल डिब्बे में डालें।",
        llamaStepsTitle = "ऑन-डिवाइस Llama 3.2 चरण",
        llamaNotImported = "Llama 3.2 1B INT8 अभी इम्पोर्ट नहीं हुआ है। इम्पोर्ट करने के लिए नीचे टैप करें।",
        generatingSteps = "सुरक्षित निपटान चरण बन रहे हैं...",
        statutoryLabelsTitle = "वैधानिक डिब्बा लेबल (SWM 2026)",
        askFollowUp = "अगला प्रश्न पूछें",
        importLlamaBtn = "Llama 3.2 INT8 इम्पोर्ट करें",
        llamaReadyBadge = "Llama INT8 तैयार",
        modelNeededBadge = "मॉडल आवश्यक",
        scanFirstHint = "Llama 3.2 1B INT8 का उपयोग करने के लिए पहले किसी वस्तु को स्कैन करें।",
        importCardTitle = "ऑन-डिवाइस Llama 3.2 1B INT8 इम्पोर्ट करें",
        copyingVerifying = "कॉपी और सत्यापित हो रहा है...",
        selectModelFile = "llama3_2_1b_int8.pte चुनें",
        thinkingOnDevice = "डिवाइस पर सोच रहा है...",
        promptPrep = "इसे कैसे तैयार करें?",
        promptWhyBin = "यही डिब्बा क्यों?",
        promptImpact = "पर्यावरणीय प्रभाव?",
        askPlaceholder = "रीसाइक्लिंग या निपटान के बारे में पूछें..."
    )

    fun stringsFor(lang: AppLanguage): AppStrings = when (lang) {
        AppLanguage.EN -> en
        AppLanguage.TA -> ta
        AppLanguage.KN -> kn
        AppLanguage.HI -> hi
    }

    fun localizedClassName(raw: String, lang: AppLanguage): String = when (raw) {
        "battery" -> when (lang) {
            AppLanguage.EN -> "Electronic Waste"
            AppLanguage.TA -> "மின்னணு கழிவு"
            AppLanguage.KN -> "ಎಲೆಕ್ಟ್ರಾನಿಕ್ ತ್ಯಾಜ್ಯ"
            AppLanguage.HI -> "इलेक्ट्रॉनिक कचरा"
        }
        "plastic_bottle", "plastic" -> when (lang) {
            AppLanguage.EN -> "Plastic"
            AppLanguage.TA -> "பிளாஸ்டிக்"
            AppLanguage.KN -> "ಪ್ಲಾಸ್ಟಿಕ್"
            AppLanguage.HI -> "प्लास्टिक"
        }
        "cardboard" -> when (lang) {
            AppLanguage.EN -> "Cardboard"
            AppLanguage.TA -> "அட்டைப் பெட்டி"
            AppLanguage.KN -> "ರಟ್ಟಿನ ಪೆಟ್ಟಿಗೆ"
            AppLanguage.HI -> "गत्ता"
        }
        "paper" -> when (lang) {
            AppLanguage.EN -> "Paper"
            AppLanguage.TA -> "காகிதம்"
            AppLanguage.KN -> "ಕಾಗದ"
            AppLanguage.HI -> "कागज़"
        }
        "metal" -> when (lang) {
            AppLanguage.EN -> "Metal"
            AppLanguage.TA -> "உலோகம்"
            AppLanguage.KN -> "ಲೋಹ"
            AppLanguage.HI -> "धातु"
        }
        "glass" -> when (lang) {
            AppLanguage.EN -> "Glass"
            AppLanguage.TA -> "கண்ணாடி"
            AppLanguage.KN -> "ಗಾಜು"
            AppLanguage.HI -> "कांच"
        }
        "food_waste", "organic" -> when (lang) {
            AppLanguage.EN -> "Food / Organic Waste"
            AppLanguage.TA -> "உணவு / மக்கும் கழிவு"
            AppLanguage.KN -> "ಆಹಾರ / ಸಾವಯವ ತ್ಯಾಜ್ಯ"
            AppLanguage.HI -> "भोजन / जैविक कचरा"
        }
        "sanitary_item" -> when (lang) {
            AppLanguage.EN -> "Sanitary Waste"
            AppLanguage.TA -> "சுகாதாரக் கழிவு"
            AppLanguage.KN -> "ನೈರ್ಮಲ್ಯ ತ್ಯಾಜ್ಯ"
            AppLanguage.HI -> "सैनिटरी कचरा"
        }
        "unknown" -> when (lang) {
            AppLanguage.EN -> "Uncertain Item"
            AppLanguage.TA -> "தெளிவற்ற பொருள்"
            AppLanguage.KN -> "ಅಸ್ಪಷ್ಟ ವಸ್ತು"
            AppLanguage.HI -> "अनिश्चित वस्तु"
        }
        else -> raw.split("_").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    fun localizedBinName(stream: StreamType, fallbackEn: String, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> fallbackEn
        AppLanguage.TA -> when (stream) {
            StreamType.WET -> "பச்சை தொட்டி (மக்கும் / ஈரக் கழிவு)"
            StreamType.DRY -> "நீல தொட்டி (மறுசுழற்சி / உலர் கழிவு)"
            StreamType.SANITARY -> "மஞ்சள் தொட்டி (சுகாதாரக் கழிவு)"
            StreamType.SPECIAL_CARE -> "சிவப்பு தொட்டி (சிறப்பு / மின்னணு கழிவு)"
            StreamType.REJECT -> "கைமுறை சரிபார்ப்பு"
        }
        AppLanguage.KN -> when (stream) {
            StreamType.WET -> "ಹಸಿರು ಡಬ್ಬಿ (ಹಸಿ / ಜೈವಿಕ ತ್ಯಾಜ್ಯ)"
            StreamType.DRY -> "ನೀಲಿ ಡಬ್ಬಿ (ಒಣ / ಮರುಬಳಕೆ ತ್ಯಾಜ್ಯ)"
            StreamType.SANITARY -> "ಹಳದಿ ಡಬ್ಬಿ (ನೈರ್ಮಲ್ಯ ತ್ಯಾಜ್ಯ)"
            StreamType.SPECIAL_CARE -> "ಕೆಂಪು ಡಬ್ಬಿ (ವಿಶೇಷ / ಅಪಾಯಕಾರಿ ತ್ಯಾಜ್ಯ)"
            StreamType.REJECT -> "ಕೈಯಾರೆ ಪರಿಶೀಲನೆ"
        }
        AppLanguage.HI -> when (stream) {
            StreamType.WET -> "हरा डिब्बा (गीला / जैविक कचरा)"
            StreamType.DRY -> "नीला डिब्बा (सूखा / रीसाइक्लिंग कचरा)"
            StreamType.SANITARY -> "पीला डिब्बा (सैनिटरी कचरा)"
            StreamType.SPECIAL_CARE -> "लाल डिब्बा (विशेष / खतरनाक कचरा)"
            StreamType.REJECT -> "मैन्युअल जांच"
        }
    }
}
