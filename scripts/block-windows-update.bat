@echo off
chcp 65001 >nul
echo ============================================
echo   Windows Update 彻底封堵
echo ============================================
echo.

:: ===== 第二层：停服 + 禁服 =====
echo [1/3] 禁用 Windows Update 服务...
sc config wuauserv start= disabled
sc stop wuauserv
echo    wuauserv → DISABLED
echo.

sc config WaaSMedicSvc start= disabled
sc stop WaaSMedicSvc
echo    WaaSMedicSvc → DISABLED
echo.

:: ===== 第四层：防火墙出站封堵 =====
echo [2/3] 添加防火墙出站规则...
netsh advfirewall firewall add rule name="Block-WU-UpdateMS" dir=out action=block remoteip=13.107.4.50 enable=yes 2>nul
netsh advfirewall firewall add rule name="Block-WU-Download" dir=out action=block remoteip=13.107.18.0/24 enable=yes 2>nul
netsh advfirewall firewall add rule name="Block-WU-Delivery" dir=out action=block remoteip=2.22.58.0/24 enable=yes 2>nul
netsh advfirewall firewall add rule name="Block-WU-Edge" dir=out action=block remoteip=13.107.21.200 enable=yes 2>nul
echo    4 条出站规则已添加
echo.

:: ===== 验证 =====
echo [3/3] 验证...
echo.
sc query wuauserv | findstr STATE
sc qc wuauserv | findstr START_TYPE
echo.
netsh advfirewall firewall show rule name="Block-WU-UpdateMS" | findstr "已启用"
netsh advfirewall firewall show rule name="Block-WU-Download" | findstr "已启用"
echo.
echo ============================================
echo   搞定了，重启生效
echo ============================================
pause
