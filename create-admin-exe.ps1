# PowerShell script to create an admin-required executable wrapper
$source = @'
using System;
using System.Diagnostics;
using System.IO;
using System.Security.Principal;
using System.Windows.Forms;

namespace WosBotAdmin
{
    class Program
    {
        [STAThread]
        static void Main()
        {
            try
            {
                // Check if running as administrator
                bool isAdmin = new WindowsPrincipal(WindowsIdentity.GetCurrent())
                    .IsInRole(WindowsBuiltInRole.Administrator);

                if (!isAdmin)
                {
                    // Request administrator privileges
                    ProcessStartInfo startInfo = new ProcessStartInfo();
                    startInfo.UseShellExecute = true;
                    startInfo.WorkingDirectory = Environment.CurrentDirectory;
                    startInfo.FileName = System.Reflection.Assembly.GetExecutingAssembly().Location;
                    startInfo.Verb = "runas";
                    
                    try
                    {
                        Process.Start(startInfo);
                    }
                    catch (System.ComponentModel.Win32Exception)
                    {
                        MessageBox.Show("Administrator privileges are required to run WosBot.\n\nEXE created by: Stargaterunner", 
                            "WosBot v1.5.4 - EXE by Stargaterunner", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                        return;
                    }
                    
                    return;
                }

                // We are admin, now start the actual WosBot application
                string exePath = Path.Combine(Application.StartupPath, "WosBot.exe");
                string tessdataPath = Path.Combine(Application.StartupPath, "lib", "tesseract");
                
                // Fallback to app/lib/tesseract if lib/tesseract doesn't exist
                if (!Directory.Exists(tessdataPath))
                {
                    tessdataPath = Path.Combine(Application.StartupPath, "app", "lib", "tesseract");
                }
                
                if (File.Exists(exePath))
                {
                    ProcessStartInfo wosBot = new ProcessStartInfo();
                    wosBot.FileName = exePath;
                    wosBot.WorkingDirectory = Application.StartupPath;
                    wosBot.UseShellExecute = false;
                    
                    // Set Tesseract environment variable
                    wosBot.EnvironmentVariables["TESSDATA_PREFIX"] = tessdataPath;
                    
                    Process.Start(wosBot);
                }
                else
                {
                    MessageBox.Show("WosBot.exe not found in the same directory!\n\nEXE created by: Stargaterunner", 
                        "WosBot v1.5.4 - EXE by Stargaterunner", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Error starting WosBot: " + ex.Message + "\n\nEXE created by: Stargaterunner", 
                    "WosBot v1.5.4 - EXE by Stargaterunner", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
'@

# Compile the C# source to create the admin wrapper
Add-Type -TypeDefinition $source -ReferencedAssemblies System.Windows.Forms -OutputAssembly "dist\WosBot\WosBotAdmin.exe" -OutputType WindowsApplication

Write-Host "WosBotAdmin.exe created successfully!"
Write-Host "This executable will always request administrator privileges before starting WosBot."