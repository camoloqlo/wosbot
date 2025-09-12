

# Whiteout Survival Bot - Beta Fork (Personal Optimizations)

**This is my personal beta fork of the original [wos-bot](https://github.com/your-original-wos-bot-link).**

## 🚀 My Personal Changes & Optimizations (Unique to This Fork)

- **Absolute Intel Priority:** Intel tasks always take precedence over Gathering. Gathering will always wait if Intel is active or scheduled. No more mutual waiting.
- **PetSkillsTask Improvements:** If a skill is "Active", the task is rescheduled for 1 hour instead of looping. Robust cooldown and error handling.
- **Storehouse Chest OCR:** Improved time recognition, error handling, and only pressing the back button when something is actually claimed.
- **Stamina OCR Fix:** Correctly recognizes stamina values over 1000.
- **March Queue Coordination:** No more march slot conflicts between Intel and Gathering.
- **Batch File Improvements:** Admin batch file for MEmu testing, with robust error handling and auto-detection of latest JAR.
- **English Documentation:** All docs and logs are now in English for better maintainability.
- **Project Hygiene:** .gitignore, README, and build scripts improved for internal use.
- **Regular Upstream Sync:** I regularly merge improvements from the original repository to keep this fork up to date.

> **This fork is for my own experiments and workflow improvements. It may contain features, workarounds, or changes not present in the official version. Use at your own risk!**

---

## 🧩 Features Inherited from the Original wos-bot

The following features are provided by the original wos-bot project and are available here as well:

- Multi-profile support (run multiple accounts simultaneously)
- Automates daily **Nomadic Merchant** interactions
- Automatically buys **VIP points** from the merchant
- **Hero Recruitment** automation
- Collects **Daily Shards** from the **War Academy**
- Collects **Fire Crystals** from the **Crystal Laboratory**
- Opens **Exploration Chests**
- Claims **Daily VIP Points**
- Contributes to **Alliance Tech**
- Collects **Alliance Chests**
- Auto **Trains and Promotes Troops**
- Auto activates **Pet Skills** (Food, Treasure and Stamina)
- Claims **Online Rewards**
- Claims **Pet Adventure** chests
- Auto-collect rewards from mail
- **Alliance Auto Join** for rallies
- Automatically **Gathers** resources
- Automate **Intel** completion
- Claims **Tundra Trek Supplies**
- Automates **Tundra Truck Event** "My Trucks" section

---



## 🔧 Recent Bugfixes

### PetSkillsTask: Improved Gathering Logic
- **Problem:** When "Active" status was detected, gathering was attempted too often (loop), even though no new start was possible.
- **Solution:**
  - When bot detects "Active", task is now rescheduled for 1 hour later (instead of immediately or endless loop).
  - Cooldown time reschedule as usual, 5 minutes on errors.
- **Status:** ✅ Fixed – Gathering only retries when sensible, no more loops on "Active"

### Storehouse Chest: Improved Time OCR, Error Handling & Back Button Logic
- **Problem:** Remaining time for Storehouse Chest was often not detected or incorrectly parsed, leading to endless loops. Back button interfered with detection.
- **Solution:**
  - Robust preprocessing and parsing for time formats (00:11:22, 11:22 etc.)
  - Error logging for unrecognizable formats
  - Automatic rescheduling on OCR errors
  - Back button only pressed when something was actually collected (Chest or Stamina)
- **Status:** ✅ Fixed - Time formats are now more robustly detected and processed, OCR runs more stable

### Stamina OCR Problem Fixed
- **Problem:** Bot only detected first digit for stamina values over 1000 (e.g., "1" instead of "1454")
- **Solution:** Improved OCR text cleaning and extended detection region
- **Status:** ✅ Fixed - Stamina values are now correctly detected

### March Queue Coordination Implemented
- **Problem:** Intel and Gathering Tasks competed for the same March slots
- **Solution:** Intel has absolute priority over Gathering Tasks
- **Details:** 
  - Intel Tasks run immediately, regardless of Gathering status
  - Gathering waits 10 minutes when Intel is active/scheduled
  - PetSkillsTask waits 1 hour when skills are "Active"
- **Status:** ✅ Implemented - Priority-based task execution

---

## ⚙️ Configuration

The bot is configured for **MEmu Android Emulator** with the following settings:

- **Resolution:** 720x1280 (320 DPI)  
- **CPU:** 2 cores  
- **RAM:** 2GB 
- **Language:** English

---

## 🛠️ Building & Running

### Building:

```sh
mvn clean install package
```
Creates a `.jar` file in the `wos-hmi/target` directory.

### Running:

#### Via Command Line (Recommended)
Running via command line shows real-time logs, which is helpful for debugging.
```sh
# Navigate to target directory and start bot
java -jar wos-bot-x.x.x.jar
```

#### Via Admin Batch File
For MEmu testing, use the included admin batch file to start with proper permissions:
```sh
# Run as administrator for MEmu compatibility
run-as-admin.bat
```

#### Double-click
The bot can also be started by double-clicking the `wos-bot-x.x.x.jar` file. No console will be shown for logs.

---

### 🔮 Test Features (In Development)
- 🔹 **Arena Battles** – Automatic arena management
- 🔹 **Beast Hunting** – Implement automatic beast hunting
- 🔹 **Polar Terror Hunting** – Implement automatic polar terror hunting
- 🔹 **And more...** 🔥