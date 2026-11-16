package com.wanbo.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests validating system privileges, permissions, and intent filter configurations
 * for the WanboSettings privileged system application.
 */
class ManifestIntentFilterTest {

    private lateinit var manifestFile: File
    private lateinit var manifestDoc: Document

    companion object {
        private const val EXPECTED_PACKAGE = "com.wanbo.settings"
        private const val EXPECTED_SHARED_USER_ID = "android.uid.system"

        private val REQUIRED_PERMISSIONS = listOf(
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.WRITE_SETTINGS",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH",
            "android.permission.SET_TIME",
            "android.permission.SET_TIME_ZONE",
            "android.permission.FORCE_STOP_PACKAGES",
            "android.permission.CLEAR_APP_CACHE",
            "android.permission.REBOOT"
        )

        private val REQUIRED_ACTIONS = listOf(
            "android.intent.action.MAIN",
            "android.settings.SETTINGS",
            "android.settings.WIFI_SETTINGS",
            "android.settings.NETWORK_OPERATOR_SETTINGS",
            "android.settings.BLUETOOTH_SETTINGS",
            "android.settings.DEVICE_INFO_SETTINGS",
            "android.settings.DATE_SETTINGS",
            "android.settings.APPLICATION_SETTINGS",
            "android.settings.MANAGE_APPLICATIONS_SETTINGS",
            "android.settings.DISPLAY_SETTINGS",
            "android.settings.SOUND_SETTINGS"
        )
    }

    /**
     * Resolves the AndroidManifest.xml file across project execution working directories.
     *
     * @return Existing File instance pointing to AndroidManifest.xml.
     */
    private fun locateManifest(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("settings/app/src/main/AndroidManifest.xml"),
            File("C:/Users/fecam/wanbo-light-os/settings/app/src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find AndroidManifest.xml among: $candidates")
    }

    /**
     * Resolves the root directory of the settings module for tree-wide compliance scanning.
     *
     * @return Existing File instance representing the settings root directory.
     */
    private fun locateSettingsRootDir(): File {
        val candidates = listOf(
            File("."),
            File(".."),
            File("settings"),
            File("C:/Users/fecam/wanbo-light-os/settings")
        )
        for (candidate in candidates) {
            val root = candidate.canonicalFile
            if (File(root, "settings.gradle.kts").exists() && File(root, "app").isDirectory) {
                return root
            }
        }
        val manifest = locateManifest()
        var parent: File? = manifest.parentFile
        while (parent != null) {
            if (File(parent, "settings.gradle.kts").exists()) {
                return parent
            }
            parent = parent.parentFile
        }
        return File("C:/Users/fecam/wanbo-light-os/settings")
    }

    /**
     * Evaluates whether a given character sequence contains any Unicode emoji codepoints.
     *
     * @param text String content to evaluate.
     * @return True if one or more emojis are detected; false otherwise.
     */
    private fun containsEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (cp in 0x1F300..0x1F5FF ||
                cp in 0x1F600..0x1F64F ||
                cp in 0x1F680..0x1F6FF ||
                cp in 0x1F700..0x1F77F ||
                cp in 0x1F780..0x1F7FF ||
                cp in 0x1F800..0x1F8FF ||
                cp in 0x1F900..0x1F9FF ||
                cp in 0x1FA00..0x1FA6F ||
                cp in 0x1FA70..0x1FAFF ||
                cp in 0x2600..0x26FF ||
                cp in 0x2700..0x27BF ||
                cp in 0xFE00..0xFE0F ||
                cp in 0x1F1E6..0x1F1FF
            ) {
                return true
            }
            i += charCount
        }
        return false
    }

    /**
     * Extracts named attribute value regardless of XML namespace prefix.
     *
     * @param element Target XML DOM element.
     * @param attributeName Local or qualified name of the attribute.
     * @return Attribute value or empty string if not present.
     */
    private fun getAttributeFlexible(element: Element, attributeName: String): String {
        if (element.hasAttribute(attributeName)) {
            return element.getAttribute(attributeName)
        }
        if (element.hasAttribute("android:$attributeName")) {
            return element.getAttribute("android:$attributeName")
        }
        val attrs = element.attributes
        for (idx in 0 until attrs.length) {
            val node = attrs.item(idx)
            val name = node.nodeName
            if (name == attributeName || name.endsWith(":$attributeName") || node.localName == attributeName) {
                return node.nodeValue ?: ""
            }
        }
        return ""
    }

    /**
     * Sets up the DOM document parser prior to each test invocation.
     */
    @Before
    fun setUp() {
        manifestFile = locateManifest()
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        manifestDoc = builder.parse(manifestFile)
    }

    /**
     * Verifies that the package name is com.wanbo.settings and sharedUserId is omitted so
     * PackageManagerService accepts the APK on MediaTek platform-signed ROMs.
     */
    @Test
    fun testPackageNameAndSharedUserId() {
        val root = manifestDoc.documentElement
        val packageName = getAttributeFlexible(root, "package")
        val sharedUserId = getAttributeFlexible(root, "sharedUserId")

        assertEquals("Manifest package must match", EXPECTED_PACKAGE, packageName)
        assertEquals("Manifest sharedUserId must be empty to prevent signature rejection", "", sharedUserId)
    }

    /**
     * Verifies that coreApp is declared as true for system boot priority.
     */
    @Test
    fun testCoreAppDeclared() {
        val root = manifestDoc.documentElement
        val coreApp = getAttributeFlexible(root, "coreApp")
        assertEquals("Manifest coreApp must be true", "true", coreApp)
    }

    /**
     * Verifies that all eleven required system permissions are declared in the manifest.
     */
    @Test
    fun testAllElevenSystemPermissionsDeclared() {
        val permissionNodes = manifestDoc.getElementsByTagName("uses-permission")
        val declaredPermissions = mutableSetOf<String>()

        for (i in 0 until permissionNodes.length) {
            val element = permissionNodes.item(i) as Element
            val name = getAttributeFlexible(element, "name")
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        assertEquals(
            "Must declare exactly 11 system permissions",
            REQUIRED_PERMISSIONS.size,
            REQUIRED_PERMISSIONS.count { declaredPermissions.contains(it) }
        )

        for (permission in REQUIRED_PERMISSIONS) {
            assertTrue("Missing required permission: $permission", declaredPermissions.contains(permission))
        }
    }

    /**
     * Verifies that all eleven required settings intent action filters are declared on SettingsActivity.
     */
    @Test
    fun testAllElevenSettingsActionsDeclared() {
        val actionNodes = manifestDoc.getElementsByTagName("action")
        val declaredActions = mutableSetOf<String>()

        for (i in 0 until actionNodes.length) {
            val element = actionNodes.item(i) as Element
            val name = getAttributeFlexible(element, "name")
            if (name.isNotEmpty()) {
                declaredActions.add(name)
            }
        }

        for (action in REQUIRED_ACTIONS) {
            assertTrue("Missing required intent filter action: $action", declaredActions.contains(action))
        }
    }

    /**
     * Verifies that MAIN action declares LAUNCHER and LEANBACK_LAUNCHER intent categories.
     */
    @Test
    fun testLauncherCategoriesDeclared() {
        val filterNodes = manifestDoc.getElementsByTagName("intent-filter")
        var hasMainWithLeanback = false
        var hasMainWithLauncher = false

        for (i in 0 until filterNodes.length) {
            val filter = filterNodes.item(i) as Element
            val actionNodes = filter.getElementsByTagName("action")
            var isMain = false
            for (j in 0 until actionNodes.length) {
                val actionElem = actionNodes.item(j) as Element
                if (getAttributeFlexible(actionElem, "name") == "android.intent.action.MAIN") {
                    isMain = true
                    break
                }
            }

            if (isMain) {
                val categoryNodes = filter.getElementsByTagName("category")
                for (k in 0 until categoryNodes.length) {
                    val catElem = categoryNodes.item(k) as Element
                    val catName = getAttributeFlexible(catElem, "name")
                    if (catName == "android.intent.category.LAUNCHER") {
                        hasMainWithLauncher = true
                    }
                    if (catName == "android.intent.category.LEANBACK_LAUNCHER") {
                        hasMainWithLeanback = true
                    }
                }
            }
        }

        assertTrue("SettingsActivity must declare LAUNCHER category for MAIN", hasMainWithLauncher)
        assertTrue("SettingsActivity must declare LEANBACK_LAUNCHER category for MAIN", hasMainWithLeanback)
    }

    /**
     * Verifies strict Zero Emojis compliance across all source and resource files in settings.
     */
    @Test
    fun testZeroEmojiComplianceAcrossSettingsProject() {
        val settingsRoot = locateSettingsRootDir()
        assertTrue("Settings root directory must exist: ${settingsRoot.absolutePath}", settingsRoot.exists())

        val validExtensions = setOf("kt", "java", "xml", "kts", "properties", "pro", "md")
        val ignoredDirs = setOf("build", ".gradle")

        val filesToCheck = mutableListOf<File>()

        /**
         * Recursively discovers all text files under target root.
         *
         * @param dir Directory to explore.
         */
        fun scanDir(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (!ignoredDirs.contains(child.name)) {
                        scanDir(child)
                    }
                } else if (child.isFile && validExtensions.contains(child.extension.lowercase())) {
                    filesToCheck.add(child)
                }
            }
        }

        scanDir(settingsRoot)
        assertTrue("Must scan at least 5 settings files", filesToCheck.size >= 5)

        for (file in filesToCheck) {
            val content = file.readText()
            assertFalse(
                "File ${file.relativeTo(settingsRoot).path} contains emoji characters violating Zero Emojis policy",
                containsEmoji(content)
            )
        }
    }
}
