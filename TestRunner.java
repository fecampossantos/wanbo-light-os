import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class TestRunner {
    private static int testsRun = 0;
    private static int testsPassed = 0;

    /**
     * Asserts that a condition is true, recording test pass/fail state.
     *
     * @param msg Descriptive error message if assertion fails.
     * @param condition Boolean condition to evaluate.
     */
    private static void assertTrue(String msg, boolean condition) {
        testsRun++;
        if (!condition) {
            System.err.println("FAIL: " + msg);
            throw new AssertionError(msg);
        }
        testsPassed++;
    }

    /**
     * Asserts that a condition is false, recording test pass/fail state.
     *
     * @param msg Descriptive error message if assertion fails.
     * @param condition Boolean condition to evaluate.
     */
    private static void assertFalse(String msg, boolean condition) {
        assertTrue(msg, !condition);
    }

    /**
     * Determines whether a given Unicode code point represents an emoji character.
     *
     * @param codePoint The Unicode code point to test.
     * @return true if codePoint is within known emoji ranges, false otherwise.
     */
    private static boolean isEmoji(int codePoint) {
        if (codePoint >= 0x1F600 && codePoint <= 0x1F64F) return true;
        if (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) return true;
        if (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) return true;
        if (codePoint >= 0x1F700 && codePoint <= 0x1F77F) return true;
        if (codePoint >= 0x1F780 && codePoint <= 0x1F7FF) return true;
        if (codePoint >= 0x1F800 && codePoint <= 0x1F8FF) return true;
        if (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) return true;
        if (codePoint >= 0x1FA00 && codePoint <= 0x1FA6F) return true;
        if (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF) return true;
        if (codePoint >= 0x2600 && codePoint <= 0x26FF) return true;
        if (codePoint >= 0x2700 && codePoint <= 0x27BF) return true;
        if (codePoint >= 0xFE00 && codePoint <= 0xFE0F) return true;
        if (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) return true;
        return Character.getType(codePoint) == Character.SURROGATE;
    }

    /**
     * Checks if a string contains any emoji character code points.
     *
     * @param text The string to inspect.
     * @return true if an emoji character is detected, false otherwise.
     */
    private static boolean containsEmoji(String text) {
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (isEmoji(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    /**
     * Resolves the resource directory path across different relative locations.
     *
     * @param subPath The subdirectory path under res.
     * @return File object pointing to the resolved resource directory.
     */
    private static File resolveResDir(String subPath) {
        File[] candidates = new File[] {
            new File("src/main/res", subPath),
            new File("app/src/main/res", subPath),
            new File("launcher/app/src/main/res", subPath)
        };
        for (File c : candidates) {
            if (c.exists() && c.isDirectory()) return c;
        }
        return new File("src/main/res", subPath);
    }

    /**
     * Main execution entry point for running the test suite verification.
     *
     * @param args Command line arguments.
     * @throws Exception If an assertion failure or I/O error occurs.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Running AssetValidationTest verification...");

        // 1. Vector Drawables Test
        File drawableDir = resolveResDir("drawable");
        String[] requiredVectors = new String[] {
            "ic_hdmi.xml",
            "ic_trapezoid_calibrate.xml",
            "ic_settings.xml",
            "ic_wifi.xml",
            "ic_launcher.xml",
            "banner.xml"
        };
        for (String name : requiredVectors) {
            File file = new File(drawableDir, name);
            assertTrue("Required vector drawable " + name + " must exist in " + drawableDir, file.exists());
            String content = Files.readString(file.toPath());
            assertTrue("Vector drawable " + name + " must declare <vector tag", content.contains("<vector"));
            assertFalse("Vector drawable " + name + " must NOT contain emojis", containsEmoji(content));
        }

        // 2. Background Drawables Test
        String[] requiredDrawables = new String[] {
            "bg_card_focused.xml",
            "bg_card_unfocused.xml",
            "bg_button_quick.xml",
            "bg_top_shelf.xml"
        };
        for (String name : requiredDrawables) {
            File file = new File(drawableDir, name);
            assertTrue("Required background drawable " + name + " must exist in " + drawableDir, file.exists());
            String content = Files.readString(file.toPath());
            assertTrue("Drawable " + name + " must declare <shape or <selector",
                content.contains("<shape") || content.contains("<selector"));
            assertFalse("Drawable " + name + " must NOT contain emojis", containsEmoji(content));
        }

        // 3. Styles Test
        File valuesDir = resolveResDir("values");
        File stylesFile = new File(valuesDir, "styles.xml");
        assertTrue("styles.xml must exist in " + valuesDir, stylesFile.exists());
        String stylesContent = Files.readString(stylesFile.toPath());
        assertTrue("styles.xml must declare Theme.WanboLauncher", stylesContent.contains("name=\"Theme.WanboLauncher\""));
        assertTrue("Theme.WanboLauncher must inherit from Theme.Leanback", stylesContent.contains("parent=\"Theme.Leanback\""));
        assertTrue("Theme.WanboLauncher must specify dark windowBackground", stylesContent.contains("windowBackground"));
        assertFalse("styles.xml must NOT contain emojis", containsEmoji(stylesContent));

        // 4. Zero Emojis across all XML
        File[] xmlFiles = drawableDir.listFiles((dir, name) -> name.endsWith(".xml"));
        File[] valFiles = valuesDir.listFiles((dir, name) -> name.endsWith(".xml"));
        assertTrue("At least one res XML file should be found", xmlFiles != null && xmlFiles.length > 0);
        for (File f : xmlFiles) {
            String content = Files.readString(f.toPath());
            assertFalse("File " + f.getName() + " violates Zero Emojis policy", containsEmoji(content));
        }
        for (File f : valFiles) {
            String content = Files.readString(f.toPath());
            assertFalse("File " + f.getName() + " violates Zero Emojis policy", containsEmoji(content));
        }

        // 5. Manifest Security Test (from Task 1)
        File manifestFile = new File("launcher/app/src/main/AndroidManifest.xml");
        if (!manifestFile.exists()) manifestFile = new File("app/src/main/AndroidManifest.xml");
        if (!manifestFile.exists()) manifestFile = new File("src/main/AndroidManifest.xml");
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists());
        String manifestContent = Files.readString(manifestFile.toPath());
        assertFalse("Manifest must NOT contain INTERNET permission", manifestContent.contains("android.permission.INTERNET"));
        assertTrue("Manifest must declare HOME category", manifestContent.contains("android.intent.category.HOME"));
        assertTrue("Manifest must declare LEANBACK_LAUNCHER category", manifestContent.contains("android.intent.category.LEANBACK_LAUNCHER"));

        // 6. Task 3 - Centralized colors.xml verification
        File colorsFile = new File(valuesDir, "colors.xml");
        assertTrue("colors.xml must exist in " + valuesDir, colorsFile.exists());
        String colorsContent = Files.readString(colorsFile.toPath());
        assertFalse("colors.xml must NOT contain emojis", containsEmoji(colorsContent));
        assertTrue("colors.xml must define bg_dark", colorsContent.contains("name=\"bg_dark\">#0A0A0E<"));
        assertTrue("colors.xml must define card_focused_stroke", colorsContent.contains("name=\"card_focused_stroke\">#FFFFFFFF<"));
        assertTrue("colors.xml must define text_primary", colorsContent.contains("name=\"text_primary\">#F3F4F6<"));
        assertTrue("colors.xml must define text_secondary", colorsContent.contains("name=\"text_secondary\">#9CA3AF<"));
        assertTrue("colors.xml must define accent_blue", colorsContent.contains("name=\"accent_blue\">#2563EB<"));

        // Verify styles.xml uses @color/bg_dark
        assertTrue("styles.xml must reference @color/bg_dark", stylesContent.contains("@color/bg_dark"));

        // 7. Task 3 - AppItem model verification
        File appItemFile = new File("launcher/app/src/main/java/com/wanbo/launcher/model/AppItem.kt");
        assertTrue("AppItem.kt must exist", appItemFile.exists());
        String appItemContent = Files.readString(appItemFile.toPath());
        assertFalse("AppItem.kt must NOT contain emojis", containsEmoji(appItemContent));
        assertTrue("AppItem.kt must declare packageName", appItemContent.contains("val packageName: String"));
        assertTrue("AppItem.kt must declare label", appItemContent.contains("val label: String"));
        assertTrue("AppItem.kt must declare isFavorite with default false", appItemContent.contains("var isFavorite: Boolean = false"));

        // 8. Task 3 - AppsRepository verification
        File repoFile = new File("launcher/app/src/main/java/com/wanbo/launcher/data/AppsRepository.kt");
        assertTrue("AppsRepository.kt must exist", repoFile.exists());
        String repoContent = Files.readString(repoFile.toPath());
        assertFalse("AppsRepository.kt must NOT contain emojis", containsEmoji(repoContent));
        assertTrue("AppsRepository must query CATEGORY_LEANBACK_LAUNCHER", repoContent.contains("CATEGORY_LEANBACK_LAUNCHER"));
        assertTrue("AppsRepository must query CATEGORY_LAUNCHER", repoContent.contains("CATEGORY_LAUNCHER"));
        assertTrue("AppsRepository must provide getLaunchableApps", repoContent.contains("fun getLaunchableApps"));
        assertTrue("AppsRepository must provide getInstalledApps", repoContent.contains("fun getInstalledApps"));
        assertTrue("AppsRepository must provide filterSelf", repoContent.contains("fun filterSelf"));
        assertTrue("AppsRepository must provide deduplicate", repoContent.contains("fun deduplicate"));
        assertTrue("AppsRepository must provide sortApps", repoContent.contains("fun sortApps"));
        assertFalse("AppsRepository must NOT call info.loadBanner", repoContent.contains("info.loadBanner"));
        assertTrue("AppsRepository must resolve banner via activityInfo/applicationInfo",
            repoContent.contains("activityInfo.loadBanner(pm) ?: activityInfo.applicationInfo?.loadBanner(pm)"));
        assertTrue("AppsRepository must filter disabled activities/applications",
            repoContent.contains("!activityInfo.isEnabled || (appInfo != null && !appInfo.enabled)"));
        assertTrue("AppsRepository must safely handle label loading", repoContent.contains("val label = try {"));
        assertTrue("AppsRepository must safely handle icon loading", repoContent.contains("val icon = try {"));
        assertTrue("AppsRepository must safely handle banner loading", repoContent.contains("val banner = try {"));

        // 9. Task 3 - AppsRepositoryTest verification
        File testFile = new File("launcher/app/src/test/java/com/wanbo/launcher/AppsRepositoryTest.kt");
        assertTrue("AppsRepositoryTest.kt must exist", testFile.exists());
        String testContent = Files.readString(testFile.toPath());
        assertFalse("AppsRepositoryTest.kt must NOT contain emojis", containsEmoji(testContent));
        assertTrue("AppsRepositoryTest must test AppItem properties", testContent.contains("testAppItemPropertiesAndDefaults"));
        assertTrue("AppsRepositoryTest must test filterSelf", testContent.contains("testFilterSelfRemovesOwnPackage"));
        assertTrue("AppsRepositoryTest must test sorting", testContent.contains("testSortAppsFavoritesFirstAndAlphabetical"));
        assertTrue("AppsRepositoryTest must test defensive loading and disabled filtering",
            testContent.contains("testDefensiveResourceResolutionAndDisabledComponentFiltering"));

        // 10. Task 4 - HardwareController verification
        File hwControllerFile = new File("launcher/app/src/main/java/com/wanbo/launcher/hardware/HardwareController.kt");
        assertTrue("HardwareController.kt must exist", hwControllerFile.exists());
        String hwControllerContent = Files.readString(hwControllerFile.toPath());
        assertFalse("HardwareController.kt must NOT contain emojis", containsEmoji(hwControllerContent));
        assertTrue("HardwareController must declare package com.wanbo.launcher.hardware",
            hwControllerContent.contains("package com.wanbo.launcher.hardware"));
        assertTrue("HardwareController must bind Keystone package com.chijimlee.trapezoid",
            hwControllerContent.contains("\"com.chijimlee.trapezoid\""));
        assertTrue("HardwareController must bind Keystone activity com.chijimlee.trapezoid.MainActivity",
            hwControllerContent.contains("\"com.chijimlee.trapezoid.MainActivity\""));
        assertTrue("HardwareController must bind HDMI package com.mediatek.tvinput",
            hwControllerContent.contains("\"com.mediatek.tvinput\""));
        assertTrue("HardwareController must bind HDMI activity com.mediatek.tvinput.TvInputActivity",
            hwControllerContent.contains("\"com.mediatek.tvinput.TvInputActivity\""));
        assertTrue("HardwareController must bind Settings package com.android.tv.settings",
            hwControllerContent.contains("\"com.android.tv.settings\""));
        assertTrue("HardwareController must bind Settings activity com.android.tv.settings.MainActivity",
            hwControllerContent.contains("\"com.android.tv.settings.MainActivity\""));
        assertTrue("HardwareController must configure Intent.FLAG_ACTIVITY_NEW_TASK",
            hwControllerContent.contains("FLAG_ACTIVITY_NEW_TASK"));
        assertTrue("HardwareController must provide createKeystoneIntent",
            hwControllerContent.contains("fun createKeystoneIntent"));
        assertTrue("HardwareController must provide createHdmiIntent",
            hwControllerContent.contains("fun createHdmiIntent"));
        assertTrue("HardwareController must provide createSettingsIntent",
            hwControllerContent.contains("fun createSettingsIntent"));
        assertTrue("HardwareController must provide launchKeystone dispatcher",
            hwControllerContent.contains("fun launchKeystone"));
        assertTrue("HardwareController must provide launchHdmi dispatcher",
            hwControllerContent.contains("fun launchHdmi"));
        assertTrue("HardwareController must provide launchSettings dispatcher",
            hwControllerContent.contains("fun launchSettings"));
        assertTrue("HardwareController must catch ActivityNotFoundException",
            hwControllerContent.contains("ActivityNotFoundException"));
        assertTrue("HardwareController must notify user via Toast",
            hwControllerContent.contains("Toast.makeText"));
        assertTrue("HardwareController must include KDoc comments",
            hwControllerContent.contains("/**") && hwControllerContent.contains("* @return"));

        // 11. Task 4 - HardwareControllerTest verification
        File hwTestFile = new File("launcher/app/src/test/java/com/wanbo/launcher/HardwareControllerTest.kt");
        assertTrue("HardwareControllerTest.kt must exist", hwTestFile.exists());
        String hwTestContent = Files.readString(hwTestFile.toPath());
        assertFalse("HardwareControllerTest.kt must NOT contain emojis", containsEmoji(hwTestContent));
        assertTrue("HardwareControllerTest must test Keystone component binding",
            hwTestContent.contains("testKeystoneComponentAndPackageBinding"));
        assertTrue("HardwareControllerTest must test HDMI component binding",
            hwTestContent.contains("testHdmiComponentAndPackageBinding"));
        assertTrue("HardwareControllerTest must test Settings component binding",
            hwTestContent.contains("testSettingsComponentAndPackageBinding"));
        assertTrue("HardwareControllerTest must test FLAG_ACTIVITY_NEW_TASK",
            hwTestContent.contains("testLaunchFlagsIncludeFlagActivityNewTask"));
        assertTrue("HardwareControllerTest must test Toast messages",
            hwTestContent.contains("testToastNotificationMessagesAreValidAndEmojiFree"));
        assertTrue("HardwareControllerTest must test Zero Emojis",
            hwTestContent.contains("testZeroEmojisInHardwareSourceAndTestFiles"));
        assertTrue("HardwareControllerTest must test KDoc and structure",
            hwTestContent.contains("testHardwareControllerSourceCodeStructureAndKDoc"));

        // 12. Task 5 - item_app_card.xml & activity_main.xml verification
        File layoutDir = resolveResDir("layout");
        File cardFile = new File(layoutDir, "item_app_card.xml");
        assertTrue("item_app_card.xml must exist in " + layoutDir, cardFile.exists());
        String cardContent = Files.readString(cardFile.toPath());
        assertFalse("item_app_card.xml must NOT contain emojis", containsEmoji(cardContent));
        assertTrue("item_app_card.xml must declare layout_width=\"120dp\"", cardContent.contains("android:layout_width=\"120dp\""));
        assertTrue("item_app_card.xml must declare layout_height=\"90dp\"", cardContent.contains("android:layout_height=\"90dp\""));
        assertTrue("item_app_card.xml must declare focusable=\"true\"", cardContent.contains("android:focusable=\"true\""));
        assertTrue("item_app_card.xml must declare @+id/ivAppIcon", cardContent.contains("@+id/ivAppIcon"));
        assertTrue("item_app_card.xml must declare @+id/tvAppLabel", cardContent.contains("@+id/tvAppLabel"));
        assertTrue("item_app_card.xml must use @drawable/bg_card_unfocused", cardContent.contains("@drawable/bg_card_unfocused"));

        File mainLayoutFile = new File(layoutDir, "activity_main.xml");
        assertTrue("activity_main.xml must exist in " + layoutDir, mainLayoutFile.exists());
        String mainLayoutContent = Files.readString(mainLayoutFile.toPath());
        assertFalse("activity_main.xml must NOT contain emojis", containsEmoji(mainLayoutContent));
        assertTrue("activity_main.xml must declare @+id/btnHdmi", mainLayoutContent.contains("@+id/btnHdmi"));
        assertTrue("activity_main.xml must declare @+id/btnKeystone", mainLayoutContent.contains("@+id/btnKeystone"));
        assertTrue("activity_main.xml must declare @+id/btnSettings", mainLayoutContent.contains("@+id/btnSettings"));
        assertTrue("activity_main.xml must declare @+id/rvFavorites", mainLayoutContent.contains("@+id/rvFavorites"));
        assertTrue("activity_main.xml must declare @+id/clockHero", mainLayoutContent.contains("@+id/clockHero"));
        assertTrue("activity_main.xml must reference @color/bg_dark", mainLayoutContent.contains("@color/bg_dark"));
        assertTrue("activity_main.xml must specify 140dp height for top shelf", mainLayoutContent.contains("140dp"));
        assertTrue("activity_main.xml must use @drawable/bg_top_shelf", mainLayoutContent.contains("@drawable/bg_top_shelf"));
        assertTrue("activity_main.xml must include hero subtitle", mainLayoutContent.contains("WanboLightOS • Modo Seguro • Zero Telemetria"));
        assertTrue("activity_main.xml must include FAVORITOS label", mainLayoutContent.contains("FAVORITOS"));
        assertTrue("activity_main.xml must specify letterSpacing 0.1", mainLayoutContent.contains("android:letterSpacing=\"0.1\""));
        assertTrue("activity_main.xml must specify horizontal orientation on rvFavorites", mainLayoutContent.contains("android:orientation=\"horizontal\""));

        // 13. Task 5 - AppCardAdapter.kt and MainActivity.kt verification
        File adapterFile = new File("launcher/app/src/main/java/com/wanbo/launcher/ui/AppCardAdapter.kt");
        assertTrue("AppCardAdapter.kt must exist", adapterFile.exists());
        String adapterContent = Files.readString(adapterFile.toPath());
        assertFalse("AppCardAdapter.kt must NOT contain emojis", containsEmoji(adapterContent));
        assertTrue("AppCardAdapter must declare SCALE_FOCUSED 1.08f", adapterContent.contains("1.08f"));
        assertTrue("AppCardAdapter must declare SCALE_UNFOCUSED 1.0f", adapterContent.contains("1.0f"));
        assertTrue("AppCardAdapter must declare ANIMATION_DURATION_MS 150L", adapterContent.contains("150L"));
        assertTrue("AppCardAdapter must declare ELEVATION_FOCUSED_DP 8f", adapterContent.contains("8f"));
        assertTrue("AppCardAdapter must use bg_card_focused", adapterContent.contains("bg_card_focused"));
        assertTrue("AppCardAdapter must use bg_card_unfocused", adapterContent.contains("bg_card_unfocused"));
        assertTrue("AppCardAdapter must provide submitList", adapterContent.contains("fun submitList"));
        assertTrue("AppCardAdapter must provide applyFocusAnimation", adapterContent.contains("fun applyFocusAnimation"));
        assertTrue("AppCardAdapter must include KDoc comments", adapterContent.contains("/**") && adapterContent.contains("*/"));

        File mainActFile = new File("launcher/app/src/main/java/com/wanbo/launcher/MainActivity.kt");
        assertTrue("MainActivity.kt must exist", mainActFile.exists());
        String mainActContent = Files.readString(mainActFile.toPath());
        assertFalse("MainActivity.kt must NOT contain emojis", containsEmoji(mainActContent));
        assertTrue("MainActivity must set activity_main layout", mainActContent.contains("setContentView(R.layout.activity_main)"));
        assertTrue("MainActivity must bind btnHdmi", mainActContent.contains("btnHdmi"));
        assertTrue("MainActivity must bind btnKeystone", mainActContent.contains("btnKeystone"));
        assertTrue("MainActivity must bind btnSettings", mainActContent.contains("btnSettings"));
        assertTrue("MainActivity must call HardwareController.launchHdmi", mainActContent.contains("HardwareController.launchHdmi"));
        assertTrue("MainActivity must call HardwareController.launchKeystone", mainActContent.contains("HardwareController.launchKeystone"));
        assertTrue("MainActivity must call HardwareController.launchSettings", mainActContent.contains("HardwareController.launchSettings"));
        assertTrue("MainActivity must initialize AppsRepository", mainActContent.contains("AppsRepository"));
        assertTrue("MainActivity must query getLeanbackLaunchIntentForPackage", mainActContent.contains("getLeanbackLaunchIntentForPackage"));
        assertTrue("MainActivity must fallback to getLaunchIntentForPackage", mainActContent.contains("getLaunchIntentForPackage"));
        assertTrue("MainActivity must add FLAG_ACTIVITY_NEW_TASK", mainActContent.contains("FLAG_ACTIVITY_NEW_TASK"));
        assertTrue("MainActivity must include KDoc comments", mainActContent.contains("/**") && mainActContent.contains("*/"));

        // 14. Task 5 - MainActivityLayoutTest verification
        File layoutTestFile = new File("launcher/app/src/test/java/com/wanbo/launcher/MainActivityLayoutTest.kt");
        assertTrue("MainActivityLayoutTest.kt must exist", layoutTestFile.exists());
        String layoutTestContent = Files.readString(layoutTestFile.toPath());
        assertFalse("MainActivityLayoutTest.kt must NOT contain emojis", containsEmoji(layoutTestContent));
        assertTrue("MainActivityLayoutTest must test activity_main structure", layoutTestContent.contains("testActivityMainXmlExistsAndStructure"));
        assertTrue("MainActivityLayoutTest must test required IDs", layoutTestContent.contains("testActivityMainRequiredIds"));
        assertTrue("MainActivityLayoutTest must test top bar hardware buttons", layoutTestContent.contains("testActivityMainTopBarHardwareButtonsAndDrawables"));
        assertTrue("MainActivityLayoutTest must test top shelf hero area", layoutTestContent.contains("testActivityMainTopShelfHeroArea"));
        assertTrue("MainActivityLayoutTest must test favorites section", layoutTestContent.contains("testActivityMainFavoritesSection"));
        assertTrue("MainActivityLayoutTest must test item_app_card structure", layoutTestContent.contains("testItemAppCardXmlStructureAndDimensions"));
        assertTrue("MainActivityLayoutTest must test adapter constants", layoutTestContent.contains("testAppCardAdapterConstants"));
        assertTrue("MainActivityLayoutTest must test adapter data operations", layoutTestContent.contains("testAppCardAdapterDataOperations"));
        assertTrue("MainActivityLayoutTest must test MainActivity bindings", layoutTestContent.contains("testMainActivitySourceCodeStructureAndBindings"));
        assertTrue("MainActivityLayoutTest must test Zero Emojis", layoutTestContent.contains("testZeroEmojisInLayoutsAndSourceFiles"));
        assertTrue("MainActivityLayoutTest must test KDoc coverage", layoutTestContent.contains("testKDocCoverageInSourceFiles"));

        // 15. Task 6 - OS Pipeline: Debloat Specification & build.prop Patch verification
        File debloatFile = new File("os-pipeline/patches/debloat-list.txt");
        assertTrue("debloat-list.txt must exist in os-pipeline/patches/", debloatFile.exists());
        String debloatContent = Files.readString(debloatFile.toPath());
        assertFalse("debloat-list.txt must NOT contain emojis", containsEmoji(debloatContent));
        assertTrue("debloat-list.txt must include Zeasn/Whale advertising apps", debloatContent.contains("TV_OPEN_LAUNCHER_WHALE") && debloatContent.contains("Zeasn"));
        assertTrue("debloat-list.txt must include demo app fusion.android.tv.demo", debloatContent.contains("fusion.android.tv.demo"));
        assertTrue("debloat-list.txt must include test suite com.mstar.android.media.tests", debloatContent.contains("com.mstar.android.media.tests"));
        assertTrue("debloat-list.txt must include mediatek.factorymenu.ui", debloatContent.contains("mediatek.factorymenu.ui"));
        assertTrue("debloat-list.txt must include fake cleaner MemoryClean", debloatContent.contains("MemoryClean"));
        assertTrue("debloat-list.txt must include fake cleaner CleanCacheUtil", debloatContent.contains("CleanCacheUtil"));
        assertTrue("debloat-list.txt must include PrintSpooler", debloatContent.contains("PrintSpooler"));
        assertTrue("debloat-list.txt must include Browser", debloatContent.contains("Browser"));
        assertTrue("debloat-list.txt must include GoogleContactsSyncAdapter", debloatContent.contains("GoogleContactsSyncAdapter"));
        assertTrue("debloat-list.txt must include CalendarProvider", debloatContent.contains("CalendarProvider"));

        // Critical HAL & Driver safety checks: MUST NEVER be purged
        String[] criticalBlacklist = new String[] {
            "trapezoidServer",
            "com.chijimlee.trapezoid",
            "com.mediatek.tvinput",
            "dtv_svc",
            "com.mediatek.autopair",
            "com.mediatek.loadingequalizer",
            "com.android.tv.settings",
            "kxtj2server",
            "hotkey.dispatcher"
        };
        for (String crit : criticalBlacklist) {
            assertFalse("debloat-list.txt must NEVER include critical HAL/driver: " + crit,
                debloatContent.toLowerCase().contains(crit.toLowerCase()));
        }

        File buildPropFile = new File("os-pipeline/patches/build.prop.patch");
        assertTrue("build.prop.patch must exist in os-pipeline/patches/", buildPropFile.exists());
        String buildPropContent = Files.readString(buildPropFile.toPath());
        assertFalse("build.prop.patch must NOT contain emojis", containsEmoji(buildPropContent));
        assertTrue("build.prop.patch must declare ro.config.low_ram=true", buildPropContent.contains("ro.config.low_ram=true"));
        assertTrue("build.prop.patch must declare ro.lmk.low=1001", buildPropContent.contains("ro.lmk.low=1001"));
        assertTrue("build.prop.patch must declare ro.lmk.medium=800", buildPropContent.contains("ro.lmk.medium=800"));
        assertTrue("build.prop.patch must declare ro.lmk.critical=0", buildPropContent.contains("ro.lmk.critical=0"));
        assertTrue("build.prop.patch must declare dalvik.vm.heapstartsize=8m", buildPropContent.contains("dalvik.vm.heapstartsize=8m"));
        assertTrue("build.prop.patch must declare dalvik.vm.heapgrowthlimit=64m", buildPropContent.contains("dalvik.vm.heapgrowthlimit=64m"));
        assertTrue("build.prop.patch must declare dalvik.vm.heapsize=128m", buildPropContent.contains("dalvik.vm.heapsize=128m"));
        assertTrue("build.prop.patch must declare dalvik.vm.heaptargetutilization=0.75", buildPropContent.contains("dalvik.vm.heaptargetutilization=0.75"));
        assertTrue("build.prop.patch must declare dalvik.vm.heapminfree=512k", buildPropContent.contains("dalvik.vm.heapminfree=512k"));
        assertTrue("build.prop.patch must declare dalvik.vm.heapmaxfree=2m", buildPropContent.contains("dalvik.vm.heapmaxfree=2m"));
        assertTrue("build.prop.patch must declare debug.sf.hw=1", buildPropContent.contains("debug.sf.hw=1"));
        assertTrue("build.prop.patch must declare video.accelerate.hw=1", buildPropContent.contains("video.accelerate.hw=1"));
        assertTrue("build.prop.patch must declare ro.hwui.renderer=opengl", buildPropContent.contains("ro.hwui.renderer=opengl"));
        assertTrue("build.prop.patch must declare ro.zram.mark_idle_delay_mins=60", buildPropContent.contains("ro.zram.mark_idle_delay_mins=60"));

        File testPyFile = new File("os-pipeline/tests/test_patches.py");
        assertTrue("test_patches.py must exist in os-pipeline/tests/", testPyFile.exists());
        String testPyContent = Files.readString(testPyFile.toPath());
        assertFalse("test_patches.py must NOT contain emojis", containsEmoji(testPyContent));
        assertTrue("test_patches.py must define TestPatches", testPyContent.contains("class TestPatches"));
        assertTrue("test_patches.py must validate zero emojis", testPyContent.contains("test_debloat_list_zero_emojis"));
        assertTrue("test_patches.py must validate required purges", testPyContent.contains("test_debloat_list_contains_required_purges"));
        assertTrue("test_patches.py must validate critical HAL preservation", testPyContent.contains("test_debloat_list_preserves_critical_hal_and_drivers"));
        assertTrue("test_patches.py must validate low ram properties", testPyContent.contains("test_build_prop_low_ram_properties"));
        assertTrue("test_patches.py must validate Dalvik heap properties", testPyContent.contains("test_build_prop_dalvik_heap_properties"));
        assertTrue("test_patches.py must validate GPU and ZRAM properties", testPyContent.contains("test_build_prop_gpu_and_zram_properties"));

        // =====================================================================
        // SECTION 16: Task 7 - Firmware Unpack, Debloat, Injection & Repack
        // =====================================================================
        File buildRomPy = new File("os-pipeline/build_rom.py");
        assertTrue("os-pipeline/build_rom.py must exist", buildRomPy.exists());
        String buildRomPyContent = Files.readString(buildRomPy.toPath());
        assertFalse("os-pipeline/build_rom.py must NOT contain emojis", containsEmoji(buildRomPyContent));
        assertTrue("build_rom.py must define run_pipeline", buildRomPyContent.contains("def run_pipeline"));
        assertTrue("build_rom.py must define apply_debloat", buildRomPyContent.contains("def apply_debloat"));
        assertTrue("build_rom.py must define inject_packages", buildRomPyContent.contains("def inject_packages"));
        assertTrue("build_rom.py must define patch_build_prop", buildRomPyContent.contains("def patch_build_prop"));
        assertTrue("build_rom.py must define CriticalHardwareSafetyViolation", buildRomPyContent.contains("class CriticalHardwareSafetyViolation"));
        assertTrue("build_rom.py must define CRITICAL_HARDWARE_BLACKLIST", buildRomPyContent.contains("CRITICAL_HARDWARE_BLACKLIST"));
        assertTrue("build_rom.py must protect trapezoid in blacklist", buildRomPyContent.contains("\"trapezoid\""));
        assertTrue("build_rom.py must protect tvinput in blacklist", buildRomPyContent.contains("\"tvinput\""));
        assertTrue("build_rom.py must protect autopair in blacklist", buildRomPyContent.contains("\"autopair\""));
        assertTrue("build_rom.py must protect loadingequalizer in blacklist", buildRomPyContent.contains("\"loadingequalizer\""));
        assertTrue("build_rom.py must protect tv.settings in blacklist", buildRomPyContent.contains("\"tv.settings\""));

        File buildRomSh = new File("os-pipeline/build-rom.sh");
        assertTrue("os-pipeline/build-rom.sh must exist", buildRomSh.exists());
        String buildRomShContent = Files.readString(buildRomSh.toPath());
        assertFalse("os-pipeline/build-rom.sh must NOT contain emojis", containsEmoji(buildRomShContent));
        assertTrue("build-rom.sh must use bash shebang", buildRomShContent.startsWith("#!/usr/bin/env bash"));
        assertTrue("build-rom.sh must set -euo pipefail", buildRomShContent.contains("set -euo pipefail"));
        assertTrue("build-rom.sh must call build_rom.py", buildRomShContent.contains("build_rom.py"));

        File makefile = new File("Makefile");
        assertTrue("Makefile must exist in root", makefile.exists());
        String makefileContent = Files.readString(makefile.toPath());
        assertFalse("Makefile must NOT contain emojis", containsEmoji(makefileContent));
        assertTrue("Makefile must declare help target", makefileContent.contains("help:"));
        assertTrue("Makefile must declare launcher target", makefileContent.contains("launcher:"));
        assertTrue("Makefile must declare test target", makefileContent.contains("test:"));
        assertTrue("Makefile must declare rom target", makefileContent.contains("rom:"));

        File testPipelinePy = new File("os-pipeline/tests/test_build_pipeline.py");
        assertTrue("test_build_pipeline.py must exist", testPipelinePy.exists());
        String testPipelinePyContent = Files.readString(testPipelinePy.toPath());
        assertFalse("test_build_pipeline.py must NOT contain emojis", containsEmoji(testPipelinePyContent));
        assertTrue("test_build_pipeline.py must define TestBuildPipeline", testPipelinePyContent.contains("class TestBuildPipeline"));
        assertTrue("test_build_pipeline.py must test CLI defaults", testPipelinePyContent.contains("test_cli_defaults"));
        assertTrue("test_build_pipeline.py must test glob expansion", testPipelinePyContent.contains("test_glob_expansion"));
        assertTrue("test_build_pipeline.py must test HAL preservation blacklist", testPipelinePyContent.contains("test_is_blacklisted_detects_all_critical_hardware_components"));
        assertTrue("test_build_pipeline.py must test dry-run pipeline execution", testPipelinePyContent.contains("test_pipeline_dry_run_end_to_end"));
        assertTrue("test_build_pipeline.py must test zero emojis compliance", testPipelinePyContent.contains("test_build_rom_py_zero_emojis"));
        assertTrue("build_rom.py must define repack_system_image", buildRomPyContent.contains("def repack_system_image"));
        assertTrue("build_rom.py must invoke python scripts via sys.executable", buildRomPyContent.contains("sys.executable"));
        assertFalse("build-rom.sh must not contain bare JSDoc delimiters", buildRomShContent.contains("\n/**"));
        assertTrue("Makefile must declare all target", makefileContent.contains("all: help"));
        assertTrue("test_build_pipeline.py must test repack_system_image", testPipelinePyContent.contains("test_repack_system_image_dry_run"));

        // =====================================================================
        // SECTION 17: Task 8 - End-User Documentation, Release Guide & License
        // =====================================================================
        File readmeFile = new File("README.md");
        assertTrue("README.md must exist in root", readmeFile.exists());
        String readmeContent = Files.readString(readmeFile.toPath());
        assertFalse("README.md must NOT contain emojis", containsEmoji(readmeContent));
        assertTrue("README.md must reference Wanbo T2 Max", readmeContent.contains("Wanbo T2 Max"));
        assertTrue("README.md must reference armeabi-v7a", readmeContent.contains("armeabi-v7a"));
        assertTrue("README.md must reference Android 9", readmeContent.contains("Android 9"));
        assertTrue("README.md must declare Zero Emojis guarantee", readmeContent.contains("Zero Emojis"));
        assertTrue("README.md must declare Zero Telemetry guarantee", readmeContent.contains("Zero Telemetry"));
        assertTrue("README.md must reference lack of INTERNET permission", readmeContent.contains("android.permission.INTERNET"));
        assertTrue("README.md must reference 350 MB RAM savings", readmeContent.contains("350 MB"));
        assertTrue("README.md must reference Hardware Preservation", readmeContent.contains("Hardware Preservation"));
        assertTrue("README.md must link to how-to-flash.md", readmeContent.contains("how-to-flash.md"));
        assertTrue("README.md must link to how-to-build.md", readmeContent.contains("how-to-build.md"));
        assertTrue("README.md must reference LICENSE", readmeContent.contains("LICENSE"));

        File flashGuideFile = new File("docs/how-to-flash.md");
        assertTrue("docs/how-to-flash.md must exist", flashGuideFile.exists());
        String flashGuideContent = Files.readString(flashGuideFile.toPath());
        assertFalse("docs/how-to-flash.md must NOT contain emojis", containsEmoji(flashGuideContent));
        assertTrue("how-to-flash.md must detail FAT32 format", flashGuideContent.contains("FAT32"));
        assertTrue("how-to-flash.md must reference WanboLightOS_v1.0.bin", flashGuideContent.contains("WanboLightOS_v1.0.bin"));
        assertTrue("how-to-flash.md must describe power off", flashGuideContent.contains("Power off") || flashGuideContent.contains("power off"));
        assertTrue("how-to-flash.md must specify rear USB port", flashGuideContent.contains("rear USB port"));
        assertTrue("how-to-flash.md must instruct holding Power button", flashGuideContent.contains("Power button"));
        assertTrue("how-to-flash.md must specify 5 seconds hold time", flashGuideContent.contains("5 seconds"));
        assertTrue("how-to-flash.md must reference MStar upgrade screen", flashGuideContent.contains("MStar"));
        assertTrue("how-to-flash.md must specify 2-3 minutes wait time", flashGuideContent.contains("2-3 minutes") || flashGuideContent.contains("2 to 3 minutes"));
        assertTrue("how-to-flash.md must describe MBR partition table", flashGuideContent.contains("MBR"));

        File buildGuideFile = new File("docs/how-to-build.md");
        assertTrue("docs/how-to-build.md must exist", buildGuideFile.exists());
        String buildGuideContent = Files.readString(buildGuideFile.toPath());
        assertFalse("docs/how-to-build.md must NOT contain emojis", containsEmoji(buildGuideContent));
        assertTrue("how-to-build.md must list Python 3 prerequisite", buildGuideContent.contains("Python 3"));
        assertTrue("how-to-build.md must list JDK 17 prerequisite", buildGuideContent.contains("JDK 17"));
        assertTrue("how-to-build.md must list Android SDK prerequisite", buildGuideContent.contains("Android SDK"));
        assertTrue("how-to-build.md must list MStar tools prerequisite", buildGuideContent.contains("MStar"));
        assertTrue("how-to-build.md must document make launcher", buildGuideContent.contains("make launcher"));
        assertTrue("how-to-build.md must document make rom", buildGuideContent.contains("make rom"));
        assertTrue("how-to-build.md must document make test", buildGuideContent.contains("make test"));
        assertTrue("how-to-build.md must document make clean", buildGuideContent.contains("make clean"));
        assertTrue("how-to-build.md must document CRITICAL_HARDWARE_BLACKLIST", buildGuideContent.contains("CRITICAL_HARDWARE_BLACKLIST"));

        File licenseFile = new File("LICENSE");
        assertTrue("LICENSE must exist in root", licenseFile.exists());
        String licenseContent = Files.readString(licenseFile.toPath());
        assertFalse("LICENSE must NOT contain emojis", containsEmoji(licenseContent));
        assertTrue("LICENSE must be GPL-3.0", licenseContent.contains("GNU GENERAL PUBLIC LICENSE"));
        assertTrue("LICENSE must state Version 3", licenseContent.contains("Version 3, 29 June 2007"));
        assertTrue("LICENSE must cite Free Software Foundation", licenseContent.contains("Free Software Foundation"));

        File testDocsFile = new File("docs/tests/test_docs.py");
        assertTrue("docs/tests/test_docs.py must exist", testDocsFile.exists());
        String testDocsContent = Files.readString(testDocsFile.toPath());
        assertFalse("docs/tests/test_docs.py must NOT contain emojis", containsEmoji(testDocsContent));
        assertTrue("test_docs.py must define TestDocs", testDocsContent.contains("class TestDocs"));
        assertTrue("test_docs.py must test readme", testDocsContent.contains("test_readme_exists_and_content"));
        assertTrue("test_docs.py must test flash guide", testDocsContent.contains("test_how_to_flash_exists_and_content"));
        assertTrue("test_docs.py must test build guide", testDocsContent.contains("test_how_to_build_exists_and_content"));
        assertTrue("test_docs.py must test license", testDocsContent.contains("test_license_exists_and_content"));
        assertTrue("test_docs.py must test zero emojis", testDocsContent.contains("test_all_documentation_files_zero_emojis"));
        assertTrue("Makefile must include docs/tests in test target", makefileContent.contains("docs/tests"));

        // 18. Task 1 - FavoriteAppsManager and AppsRepository favorites/LiveTV verification
        File favManagerFile = new File("launcher/app/src/main/java/com/wanbo/launcher/data/FavoriteAppsManager.kt");
        assertTrue("FavoriteAppsManager.kt must exist", favManagerFile.exists());
        String favManagerContent = Files.readString(favManagerFile.toPath());
        assertFalse("FavoriteAppsManager.kt must NOT contain emojis", containsEmoji(favManagerContent));
        assertTrue("FavoriteAppsManager must declare PREFS_NAME", favManagerContent.contains("PREFS_NAME"));
        assertTrue("FavoriteAppsManager must declare KEY_FAVORITES", favManagerContent.contains("KEY_FAVORITES"));
        assertTrue("FavoriteAppsManager must declare DEFAULT_FAVORITES", favManagerContent.contains("DEFAULT_FAVORITES"));
        assertTrue("FavoriteAppsManager must include YouTube TV in defaults", favManagerContent.contains("com.google.android.youtube.tv"));
        assertTrue("FavoriteAppsManager must include Netflix in defaults", favManagerContent.contains("com.netflix.ninja"));
        assertTrue("FavoriteAppsManager must include Prime Video in defaults", favManagerContent.contains("com.amazon.amazonvideo.livingroom"));
        assertTrue("FavoriteAppsManager must include Disney+ in defaults", favManagerContent.contains("com.disney.disneyplus"));
        assertTrue("FavoriteAppsManager must include Aptoide TV in defaults", favManagerContent.contains("cm.aptoidetv.pt"));
        assertTrue("FavoriteAppsManager must provide getFavoritePackageNames", favManagerContent.contains("fun getFavoritePackageNames"));
        assertTrue("FavoriteAppsManager must provide isFavorite", favManagerContent.contains("fun isFavorite"));
        assertTrue("FavoriteAppsManager must provide addFavorite", favManagerContent.contains("fun addFavorite"));
        assertTrue("FavoriteAppsManager must provide removeFavorite", favManagerContent.contains("fun removeFavorite"));
        assertTrue("FavoriteAppsManager must provide fromContext factory", favManagerContent.contains("fun fromContext"));

        // Verify AppsRepository LiveTV exclusion and filterExcluded
        assertTrue("AppsRepository must define EXCLUDED_PACKAGES", repoContent.contains("EXCLUDED_PACKAGES"));
        assertTrue("AppsRepository must exclude com.mediatek.tvinput", repoContent.contains("\"com.mediatek.tvinput\""));
        assertTrue("AppsRepository must exclude com.android.tv", repoContent.contains("\"com.android.tv\""));
        assertTrue("AppsRepository must provide isExcluded", repoContent.contains("fun isExcluded"));
        assertTrue("AppsRepository must provide filterExcluded", repoContent.contains("fun filterExcluded"));

        File favTestFile = new File("launcher/app/src/test/java/com/wanbo/launcher/FavoriteAppsManagerTest.kt");
        assertTrue("FavoriteAppsManagerTest.kt must exist", favTestFile.exists());
        String favTestContent = Files.readString(favTestFile.toPath());
        assertFalse("FavoriteAppsManagerTest.kt must NOT contain emojis", containsEmoji(favTestContent));
        assertTrue("FavoriteAppsManagerTest must test default favorites", favTestContent.contains("testDefaultFavoritesWhenStorageIsEmpty"));
        assertTrue("FavoriteAppsManagerTest must test adding favorites", favTestContent.contains("testAddFavoriteUpdatesStorage"));
        assertTrue("FavoriteAppsManagerTest must test removing favorites", favTestContent.contains("testRemoveFavoriteUpdatesStorage"));
        assertTrue("FavoriteAppsManagerTest must test duplicate prevention", favTestContent.contains("testPreventDuplicateAdditions"));
        assertTrue("FavoriteAppsManagerTest must test empty/whitespace handling", favTestContent.contains("testEmptyOrWhitespacePackageHandling"));
        assertTrue("FavoriteAppsManagerTest must test LiveTV filtering", favTestContent.contains("testLiveTvFilteringInAppsRepository"));

        System.out.println("ALL TESTS PASSED: " + testsPassed + "/" + testsRun + " assertions verified.");
    }
}
