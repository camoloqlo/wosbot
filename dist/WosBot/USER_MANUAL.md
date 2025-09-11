# 🎮 WosBot - Whiteout Survival Bot
## Complete User Manual

**🔧 Windows EXE created by:** `Stargaterunner`  
**📅 EXE Build:** September 2025  
**🏗️ Original Developer:** CamoDev

---

## 📦 Quick Start

### ✅ Recommended Execution (with Admin Rights):
```
WosBotAdmin.exe
```
- **Automatically starts as Administrator**
- **Tesseract OCR correctly configured**
- **Optimal performance**

### 🔧 Alternative Launch Options:
- `WosBot-Admin.bat` - Admin batch file
- `WosBot-Start.bat` - Normal execution with Tesseract fix

---

## ⚙️ System Requirements

### 🖥️ **Android Emulator Settings (MANDATORY)**
**Supported Emulators:**
- **MuMu Player** (Original development)
- **MEmu Player** (EXE version tested)
- **LDPlayer** (Available in bot selection)

**Settings for all emulators:
- **Resolution:** 720x1280 (320 DPI)
- **CPU:** 2 Cores
- **RAM:** 2GB
- **Language:** English
- **Game:** Whiteout Survival (in English)

### 💻 **Windows System:**
- Windows 10/11 (64-bit)
- Java Runtime (embedded in EXE)
- 550MB+ free storage space
- Administrator rights (recommended)

---

## 🎯 Bot Features

### 🏪 **Automated Daily Tasks**
- ✅ **Nomadic Merchant** - Automatic interactions and VIP point purchases
- ✅ **Hero Recruitment** - Automatically recruit heroes
- ✅ **War Academy** - Collect daily shards
- ✅ **Crystal Laboratory** - Collect fire crystals
- ✅ **Exploration Chests** - Open chests automatically
- ✅ **Daily VIP Points** - Claim VIP points
- ✅ **Online Rewards** - Collect online rewards
- ✅ **Mail Rewards** - Automatically collect mail rewards

### 🤝 **Alliance Functions**
- ✅ **Alliance Tech** - Technology contributions
- ✅ **Alliance Chests** - Collect alliance chests
- ✅ **Alliance Auto Join** - Automatically join rallies
- ✅ **Alliance Help** - Automatic help for alliance members

### 🏗️ **City & Resources**
- ✅ **Train and Promote Troops** - Train and promote troops
- ✅ **Gathering** - Automatic resource gathering
- ✅ **Building Upgrades** - Building upgrades
- ✅ **Storehouse Chest** - Storehouse chests

### 🐾 **Pet System**
- ✅ **Pet Skills** - Auto-activation (Food, Treasure, Stamina)
- ✅ **Pet Adventure** - Collect adventure chests
- ✅ **Pet Alliance Treasures** - Collect alliance treasures

### 📊 **Events & Special Tasks**
- ✅ **Intel Completion** - Automatically complete intel
- ✅ **Tundra Trek Supplies** - Collect Tundra Trek supplies
- ✅ **Tundra Truck Event** - Automate "My Trucks" section
- ✅ **Daily Missions** - Daily missions
- ✅ **Mystery Shop** - Mystery shop interactions
- ✅ **Hero Mission Events** - Hero mission events

---

## 🖱️ User Interface

### 📋 **Main Window Areas:**
1. **Launcher** - Bot start and profile selection
2. **Task Manager** - Schedule and monitor tasks
3. **Profile Manager** - Multi-account management
4. **Console Log** - Real-time logs and status
5. **Emulator Config** - Emulator connection settings

### 🎛️ **Configuration Areas:**
- **City Events** - City event settings
- **City Upgrades** - Building upgrade configuration
- **Alliance** - Alliance-specific settings
- **Gathering** - Resource gathering settings
- **Intel** - Intelligence task configuration
- **Pets** - Pet system settings
- **Training** - Troop training options
- **Events** - Event-specific configurations
- **Shop** - Shop interaction settings
- **Experts** - Expert system configuration

---

## 🚀 Getting Started

### 1️⃣ **Start Bot:**
```
Double-click on: WosBotAdmin.exe
```

### 2️⃣ **Configure Emulator:**
- Open **MuMu Player**, **MEmu Player** or **LDPlayer**
- Start Whiteout Survival
- In Bot: **"Emulator Config"** tab
- **"Detect Emulator"** or manually enter port

### 3️⃣ **Create Profile:**
- **"Profile Manager"** tab
- **"New Profile"** button
- Enter account details:
  - **Profile Name** (e.g., "Main Account")
  - **Player Level**
  - **Alliance Name**
  - **Preferred Settings**

### 4️⃣ **Configure Tasks:**
- **"Task Manager"** tab
- Enable/disable desired tasks
- Set schedules
- Set priorities

### 5️⃣ **Activate Bot:**
- **"Launcher"** tab
- Select profile
- **"Start Bot"** button

---

## 📝 Profile Management

### 👤 **Multi-Account Support:**
- Create **unlimited profiles**
- **Bulk updates** for multiple profiles
- **Copy profiles** for similar accounts
- **Import/Export** profile settings

### ⚙️ **Profile Configuration:**
```
- Account Name
- Player Level
- Alliance Information
- Task Preferences
- Schedule Settings
- Resource Priorities
- Event Preferences
```

---

## ⏰ Task Scheduling

### 📅 **Automatic Scheduling:**
- **Daily Tasks** - Daily at set times
- **Interval Tasks** - Every X minutes/hours
- **Event-based** - Depending on game events
- **Priority System** - Important tasks first

### 🎯 **Task Categories:**
- **High Priority** - VIP, Daily Rewards, Alliance
- **Medium Priority** - Gathering, Training, Pet Skills
- **Low Priority** - Upgrades, Shop Interactions
- **Event Tasks** - Time-limited events

---

## 🔧 Advanced Configuration

### 🖼️ **Template System:**
The bot uses image recognition templates in:
```
/templates/
├── alliance/
├── city/
├── events/
├── gathering/
├── pets/
├── shop/
└── training/
```

### 📊 **OCR (Optical Character Recognition):**
- **Tesseract OCR** for text recognition
- **Languages:** English, Chinese (Simplified)
- **Automatic template adaptation**

### 🗄️ **Database:**
- **SQLite** for profile and task data
- **Automatic backups**
- **Location:** `database.db`

---

## 📋 Logs & Monitoring

### 📄 **Log Files:**
```
/log/bot.log - Main log file
Console Log - Real-time output in bot
```

### 🔍 **Log Levels:**
- **INFO** - General information
- **WARN** - Warnings (not critical)
- **ERROR** - Errors (attention required)
- **DEBUG** - Detailed debugging info

---

## ❗ Troubleshooting

### 🚨 **Common Problems:**

#### 1. **"Tesseract couldn't load any languages!"**
**Solution:** 
- Use `WosBotAdmin.exe` or `WosBot-Start.bat`
- These EXE files contain all necessary configurations

#### 2. **"Emulator not detected"**
**Solution:**
- Restart emulator (MuMu/MEmu/LDPlayer)
- Enable ADB debugging
- Use port 5555 (standard)

#### 3. **"Template not found"**
**Solution:**
- Check emulator resolution: 720x1280 (320 DPI)
- Switch game to English
- Clear template cache and restart

#### 4. **"libpng warning: sBIT: invalid"**
**Solution:**
- **Harmless warning** - can be ignored
- Does not affect bot functionality

### 🔧 **Debug Mode:**
For advanced troubleshooting:
```
1. Enable Console Log
2. Set log level to DEBUG
3. Take screenshots of errors
4. Analyze log file
```

---

## ⚡ Performance Tips

### 🎯 **Optimal Performance:**
1. **Only enable needed tasks**
2. **Set reasonable intervals** (not too aggressive)
3. **Enable emulator performance mode**
4. **Close other applications during bot operation**
5. **Regular restarts** (daily)

### 📊 **Resource Usage:**
- **RAM:** ~200-400MB
- **CPU:** Low (except during template matching)
- **Network:** Minimal
- **Storage:** Logs can grow (delete regularly)

---

## 🛡️ Security & Best Practices

### ✅ **Recommended Usage:**
- **Reasonable intervals** between actions
- **Don't run 24/7**
- **Vary bot behavior**
- **Manual activity in between**

### ⚠️ **Precautions:**
- **Create profile backups**
- **Archive log files regularly**
- **Install bot updates promptly**
- **Respect game rules**

---

## 🆘 Support & Community

### 💬 **Discord Community:**
[![Discord](https://img.shields.io/badge/Discord-%235865F2.svg?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/Wk6YSr6mUp)

### ☕ **Support:**
[![Buy Me Coffee](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://buymeacoffee.com/cearivera1z)

### 🐛 **Bug Reports:**
- Detailed error description
- Attach screenshots
- Provide log files
- Include system information

---

## 📈 Version Information

**Version:** 1.5.x_BETA  
**Windows EXE Build:** September 2025 by **Stargaterunner**  
**Original JAR:** CamoDev  
**Compatibility:** Whiteout Survival (Current)

### 🔄 **Update Behavior:**
- Templates are automatically cached
- Database migrations occur automatically
- Profiles remain preserved during updates

---

## ⚖️ Legal Notice

**Disclaimer:** 
- This bot is developed for educational purposes
- Use at your own risk
- Observe game rules and ToS
- Developer assumes no liability

---

## 🎉 Success!

**The bot is successfully configured and ready to use!**

For further help, visit the Discord community or read the detailed logs in the console.

---

## 👥 Credits

**🏗️ Original Bot Development:** CamoDev  
**🔧 Windows EXE Conversion:** Stargaterunner  
**🌐 Community & Support:** [Discord](https://discord.gg/Wk6YSr6mUp)  
**☕ Support Original Developer:** [Buy Me Coffee](https://buymeacoffee.com/cearivera1z)

*📝 This manual was automatically generated based on codebase analysis of WosBot v1.5.x_BETA*  
*🔧 EXE conversion and manual by Stargaterunner - September 2025*