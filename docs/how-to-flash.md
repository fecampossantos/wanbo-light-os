# WanboLightOS USB Flashing Guide

This document provides complete, foolproof, step-by-step instructions for end-users to flash the WanboLightOS custom operating system onto the Wanbo T2 Max smart projector using a standard USB flash drive.

---

## Important Safety Information & Precautions

Flashing projector firmware modifies the low-level boot and system partitions of the device. To ensure a safe, successful installation:

1. **Uninterrupted Power Supply:** Connect the projector directly to a reliable wall power outlet. Do not use loose wall sockets or unstable extension cords.
2. **Do Not Interrupt Flashing:** Never unplug the power cable or pull out the USB drive while firmware installation is actively underway. Interrupting the flashing process may corrupt the bootloader.
3. **USB Flash Drive Compatibility:** Use a reliable USB flash drive between 4 GB and 32 GB in capacity. Drives formatted with exFAT, NTFS, or ext4 will NOT be recognized by the MStar bootloader. The drive MUST be formatted with a standard FAT32 filesystem and an MBR (Master Boot Record) partition table. GPT partition tables are not supported by the bootloader.
4. **Firmware File Integrity:** Ensure the firmware binary file is copied completely and is named exactly `WanboLightOS_v1.0.bin` (or `stock_firmware.bin` if restoring stock firmware).

---

## Prerequisites

Before beginning the installation, ensure you have:

- Wanbo T2 Max Smart Projector (`armeabi-v7a`, Android 9 Pie).
- USB thumb drive (4 GB to 32 GB, USB 2.0 or 3.0).
- Computer (Windows, macOS, or Linux) with a USB port to format the drive.
- The compiled firmware release binary: `WanboLightOS_v1.0.bin`.
- Wanbo Bluetooth or IR remote control with functional batteries (for post-flash setup).

---

## Step-by-Step Installation Guide

Follow these seven steps carefully in order:

1. Format USB drive to FAT32.
2. Copy `WanboLightOS_v1.0.bin` to root directory.
3. Power off Wanbo projector from wall power.
4. Insert USB drive into rear USB port.
5. Hold down physical Power button on top of projector while reconnecting power.
6. Keep holding for 5 seconds until MStar upgrade screen appears.
7. Release button and wait 2-3 minutes for automatic reboot into WanboLightOS.

---

### Step 1: Format USB Drive to FAT32
Format your USB thumb drive to FAT32 with an MBR partition table.

- **On Windows:**
  1. Insert the USB drive into your computer.
  2. Open "This PC" / File Explorer.
  3. Right-click the USB drive icon and select **Format...**
  4. Under "File system", select **FAT32** (Default).
  5. Under "Allocation unit size", select **4096 bytes** (or Default).
  6. Ensure "Quick Format" is checked and click **Start**.

- **On macOS:**
  1. Open **Disk Utility** (Applications -> Utilities).
  2. Select the USB flash drive in the left sidebar (select the root drive device, not just the volume).
  3. Click **Erase** in the top toolbar.
  4. Set Format to **MS-DOS (FAT)** and Scheme to **Master Boot Record**.
  5. Click **Erase**.

- **On Linux:**
  1. Identify your USB drive partition using `lsblk` (e.g. `/dev/sdb1`).
  2. Unmount the drive: `sudo umount /dev/sdb1`
  3. Format to FAT32: `sudo mkfs.vfat -F 32 -n "WANBO" /dev/sdb1`

### Step 2: Copy `WanboLightOS_v1.0.bin` to Root Directory
1. Copy the `WanboLightOS_v1.0.bin` file from your computer directly into the root directory of the USB flash drive.
   - Example path: `E:\WanboLightOS_v1.0.bin` (Windows) or `/Volumes/WANBO/WanboLightOS_v1.0.bin` (macOS).
   - Do NOT place the file inside any folder or subfolder.
2. Safely eject and remove the USB thumb drive from your computer to guarantee write caches are fully flushed.

### Step 3: Power Off Wanbo Projector from Wall Power
1. If the projector is currently on, press the power button on the remote or casing to shut it down gracefully.
2. Once the cooling fan turns off, completely unplug the projector's power cable from the wall power outlet or from the rear DC input jack.
3. Wait 10 seconds to allow all internal capacitors and circuit boards to fully discharge. Confirm that all LED indicators are completely unlit.

### Step 4: Insert USB Drive into Rear USB Port
1. Insert the prepared FAT32 USB thumb drive into the rear USB port on the Wanbo T2 Max.
2. If your model features multiple USB ports, use the primary port (labelled USB 1 or the bottom port closest to the HDMI jack).

### Step 5: Hold Down Physical Power Button While Reconnecting Power
1. Locate the physical Power button on top of the projector casing.
2. Press and firmly hold down this physical Power button with your finger.
3. While continuously holding the Power button down, plug the power cable back into the wall outlet or reconnect the DC barrel connector to the projector.

### Step 6: Keep Holding for 5 Seconds until MStar Upgrade Screen Appears
1. Continue holding down the physical Power button for at least 5 seconds after reconnecting power.
2. Observe the projected image. The MStar low-level bootloader will detect the USB firmware payload and trigger recovery upgrade mode.
3. A blue or dark recovery screen will appear on the wall/screen with the text:
   `Software Upgrade in Progress` or a graphical progress bar.
4. Once this upgrade screen appears, release the physical Power button.

### Step 7: Release Button and Wait 2-3 Minutes for Automatic Reboot
1. Allow the firmware upgrade process to complete. It will write the boot, recovery, system, and vendor partition blocks.
2. This process typically takes between 2 to 3 minutes.
3. When installation reaches 100%, the projector will automatically restart.
4. The first boot after flashing takes approximately 60 to 90 seconds while Android creates the initial Dalvik/ART runtime cache.
5. Once the clean WanboLightOS home screen appears, safely unplug the USB flash drive from the projector.

---

## Post-Installation Setup & Hardware Verification

Once WanboLightOS boots to the home screen, perform the following initial setup checks:

1. **Motorized Keystone & Focus Calibration:**
   - Click the Keystone / Trapezoid shortcut button in the top status bar.
   - The native Wanbo calibration overlay (`com.chijimlee.trapezoid`) will launch.
   - Adjust the vertical and horizontal corner angles and motorized focus using the directional pad on your remote.
2. **Bluetooth Remote Control Pairing:**
   - If using the official Wanbo Bluetooth remote, navigate to Settings -> Remote & Accessories -> Add Accessory.
   - Hold the Left and Right arrow buttons simultaneously until the pairing LED on the remote blinks.
   - The native MediaTek pairing daemon (`com.mediatek.autopair`) will detect and bind the remote.
3. **HDMI Video Input Switching:**
   - Connect your external HDMI source (gaming console, streaming stick, laptop).
   - Click the HDMI shortcut button in the top bar to switch instantly to the hardware video input feed.
4. **Network Configuration:**
   - Click the Settings button in the top bar and select Network & Internet to connect to your local 2.4 GHz or 5 GHz Wi-Fi network.

---

## Troubleshooting & Frequently Asked Questions

### The projector boots normally into the old OS instead of the upgrade screen
- **Cause 1:** The USB drive is partitioned with GPT rather than MBR. Reformat the USB drive ensuring MBR partition table scheme is selected.
- **Cause 2:** The USB filesystem is exFAT, NTFS, or corrupted. Reformat as FAT32 with 4096-byte cluster size.
- **Cause 3:** The firmware file is inside a subfolder or misnamed. The file must be located in the root directory and named `WanboLightOS_v1.0.bin`.
- **Cause 4:** The physical Power button was released too early. Ensure you press and hold the button BEFORE plugging in the power cable, and keep holding for at least 5 to 7 seconds.
- **Cause 5:** The USB port used is not wired to the bootloader's primary USB host controller. Try the other USB port if your projector has two.

### The upgrade screen freezes or shows an error
- Unplug the power cable, wait 15 seconds, and re-verify the checksum of the firmware file on your computer. Re-copy the file to the USB drive and repeat the flashing procedure.

### The projector screen remains black after flashing
- The first boot takes up to 90 seconds. Be patient and wait at least 3 minutes before power cycling.
- If the screen remains dark and the LED flashes continuously, unplug power, reconnect the USB drive, and repeat Steps 3 through 7 to re-flash the image.

### How do I return to the factory stock firmware?
- Simply repeat the exact same 7-step USB flashing procedure using the official Wanbo stock firmware binary (`stock_firmware.bin`) placed on the root of your FAT32 USB thumb drive.
