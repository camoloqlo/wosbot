

# Whiteout Survival Bot - Beta Fork (Personal Optimizations)

**This is my personal beta fork of the original [wos-bot](https://github.com/your-original-wos-bot-link).**

## 🚀 My Personal Changes & Optimizations (Unique to This Fork)

### 🎯 **Task Priority & Coordination**
- **Absolute Intel Priority:** Intel tasks always take precedence over Gathering. Gathering will always wait if Intel is active or scheduled. No more mutual waiting.
- **March Queue Coordination:** No more march slot conflicts between Intel and Gathering tasks.
- **Smart Task Rescheduling:** Improved wait times and conflict resolution between different task types.

### 🛡️ **Enhanced Task Implementations**
- **PetSkillsTask Improvements:** If a skill is "Active", the task is rescheduled for 1 hour instead of looping. Robust cooldown and error handling.
- **Storehouse Chest OCR:** Improved time recognition with support for multiple time formats (HH:mm:ss, mm:ss), better error handling, and only pressing the back button when something is actually claimed.
- **Stamina OCR Fix:** Correctly recognizes stamina values over 1000.
- **Intelligence Task Optimization:** Better march slot management and improved conflict resolution.
- **Gathering Task Enhancement:** Intelligence-aware scheduling with proper wait mechanisms.

### 🎮 **Mercenary Event Support**
- **Upstream Mercenary Event Integration:** Uses the official upstream Mercenary Event implementation from the original repository.
- **Template Resources:** Added comprehensive template image recognition for mercenary-related UI elements.
- **Clean Implementation:** Removed custom mercenary implementations in favor of upstream standards.

### 🛠️ **Development & Deployment Tools**
- **Admin Batch File:** Enhanced batch file for MEmu testing with robust error handling and auto-detection of latest JAR.
- **PowerShell Scripts:** Added `create-admin-exe.ps1` for automated admin execution setup.
- **Project Hygiene:** Improved .gitignore, README, and build scripts for better maintainability.

### 📚 **Documentation & Maintenance**
- **English Documentation:** All docs and logs are now in English for better maintainability.
- **Regular Upstream Sync:** I regularly merge improvements from the original repository to keep this fork up to date.
- **Enhanced UI Labels:** Updated UI controller with cleaner event labeling and proper configuration mapping.

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



## 🔧 Recent Improvements & Bugfixes

### ✅ **Task Coordination & Priority System**
- **Intel-Gathering Priority Fix:** Intel tasks now have absolute priority over Gathering tasks
  - Intel runs immediately regardless of Gathering status
  - Gathering waits 10 minutes when Intel is active/scheduled
  - No more march slot conflicts between task types
- **Status:** ✅ Implemented - Priority-based task execution prevents conflicts

### ✅ **PetSkillsTask: Enhanced Gathering Logic**
- **Problem:** When "Active" status was detected, gathering was attempted too often (endless loops)
- **Solution:**
  - When bot detects "Active", task is rescheduled for 1 hour later (instead of immediate retry)
  - Proper cooldown time rescheduling, 5 minutes on errors
  - Better state detection and handling
- **Status:** ✅ Fixed – Gathering only retries when sensible, no more infinite loops

### ✅ **StorehouseChest: Advanced OCR & Error Handling**
- **Problem:** Remaining time was often not detected or incorrectly parsed, causing loops
- **Solution:**
  - **Multi-format Time Parsing:** Supports HH:mm:ss, mm:ss, and malformed OCR outputs
  - **Robust Preprocessing:** Removes spaces, corrects common OCR errors (O→0, I→1, S→5)
  - **Smart Error Handling:** Automatic rescheduling on OCR failures
  - **Improved Logging:** Uses proper logging framework instead of System.err
  - **Back Button Logic:** Only pressed when rewards are actually claimed
- **Status:** ✅ Fixed - Significantly more stable OCR detection and processing

### ✅ **Stamina OCR Detection Fixed**
- **Problem:** Bot only detected first digit for stamina values over 1000 (e.g., "1" instead of "1454")
- **Solution:** Enhanced OCR text cleaning and extended detection region
- **Status:** ✅ Fixed - Stamina values above 1000 are now correctly detected

### ✅ **Mercenary Event Integration**
- **Implementation:** Integrated upstream Mercenary Event support from original repository
- **Features:**
  - Uses official upstream MercenaryEventTask implementation
  - Added comprehensive template image resources (Merc.png, Merc1.png, Merc2.png, etc.)
  - Clean UI integration with proper configuration mapping
- **Status:** ✅ Implemented - Uses stable upstream implementation

### ✅ **Development & Build Improvements**
- **Admin Execution:** Added `run-as-admin.bat` for MEmu compatibility
- **PowerShell Scripts:** Created `create-admin-exe.ps1` for automated setup
- **Enhanced .gitignore:** Better exclusion patterns for development files
- **Improved Build Process:** Updated Maven configuration and dependency management

---

## 📊 **Complete Differences from Upstream Repository**

This fork contains **540 insertions and 105 deletions** across **22 files** compared to the upstream [camoloqlo/wosbot](https://github.com/camoloqlo/wosbot) repository.

### 🔧 **Modified Core Task Files:**
- **`StorehouseChest.java`** - Enhanced OCR parsing, multi-format time support, improved error handling
- **`PetSkillsTask.java`** - Better "Active" state handling, reduced infinite loops, smarter rescheduling
- **`IntelligenceTask.java`** - Priority system implementation, march slot coordination
- **`GatherTask.java`** - Intelligence-aware scheduling, conflict avoidance mechanisms
- **`DelayedTaskRegistry.java`** - Updated task registration for new implementations

### 🎯 **Added Features:**
- **`MercenaryPrestigeTask.java`** - Custom mercenary prestige automation (136 lines)
- **6 Mercenary Template Images** - Comprehensive UI recognition resources
- **`run-as-admin.bat`** - MEmu compatibility script (77 lines)
- **`create-admin-exe.ps1`** - PowerShell automation script (87 lines)

### 🖼️ **UI & Configuration Updates:**
- **`EventsLayoutController.java`** - Enhanced event UI management
- **`LauncherLayoutController.java`** - Updated launcher interface
- **`PetsLayoutController.java`** - Improved pets management UI
- **`FXApp.java`** - Application initialization improvements
- **`EnumTemplates.java`** - Added mercenary template definitions

### 📁 **Project Infrastructure:**
- **`.gitignore`** - Enhanced exclusion patterns (18 additions)
- **`README.md`** - Comprehensive documentation (157 modifications)
- **`pom.xml`** - Build configuration updates

### 🔄 **Upstream Compatibility:**
- **Regular Sync:** This fork regularly merges upstream improvements
- **Selective Integration:** Cherry-picks beneficial changes while maintaining custom optimizations
- **Standard Compliance:** Uses upstream Mercenary Event implementation instead of custom variants

### 📈 **Statistics:**
```
Files Changed: 22
Lines Added: +540
Lines Removed: -105
Net Change: +435 lines
Binary Files: 6 (template images)
Custom Features: 4 major task improvements
Infrastructure: 3 build/deployment enhancements
```

> **Note:** All changes are designed to be compatible with the upstream repository structure while adding substantial improvements to task reliability, OCR accuracy, and user experience.

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