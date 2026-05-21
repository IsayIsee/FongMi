@echo off
chcp 65001 >nul

:: ---------------------------------------------------
:: 将本批处理复制到 FongMI media3 项目下
:: 修改下面的路径变量到实际路径
:: 运行本批处理编译并复制 aar 到指定路径
:: ---------------------------------------------------


:: 定义路径变量（根据实际情况调整绝对路径）
set "src_base=D:\Project\media\"
set "src=%src_base%libraries"
set "dst=D:\Project\TV\app\libs"
set "txt=%src_base%move.txt"

echo ===================================================
echo [1/3] 开始合并单次请求，启动 Media3 全量并行编译...
echo ===================================================

:: 使用 ^ 符号将所有编译任务合并为单条命令，大幅提升编译速度
cd %src_base%
call gradlew ^
:lib-common:assembleRelease ^
:lib-container:assembleRelease ^
:lib-database:assembleRelease ^
:lib-datasource:assembleRelease ^
:lib-datasource-okhttp:assembleRelease ^
:lib-datasource-rtmp:assembleRelease ^
:lib-decoder:assembleRelease ^
:lib-decoder-av1:assembleRelease ^
:lib-decoder-mpegh:assembleRelease ^
:lib-decoder-ffmpeg:assembleRelease ^
:lib-effect:assembleRelease ^
:lib-exoplayer:assembleRelease ^
:lib-exoplayer-dash:assembleRelease ^
:lib-exoplayer-hls:assembleRelease ^
:lib-exoplayer-rtsp:assembleRelease ^
:lib-exoplayer-smoothstreaming:assembleRelease ^
:lib-extractor:assembleRelease ^
:lib-session:assembleRelease ^
:lib-ui:assembleRelease ^
:lib-ui-danmaku:assembleRelease

:: 检查上一步 Gradle 编译是否成功
if %errorlevel% neq 0 (
    echo.
    echo ❌ 编译过程中出现错误，请检查上方日志！
    pause
    exit /b %errorlevel%
)

echo.
echo ===================================================
echo [2/3] 编译成功！正在初始化移动/同步配置...
echo ===================================================

:: 确保目标 libs 目录存在
if not exist "%dst%" (
    mkdir "%dst%"
    echo 已创建目标文件夹: %dst%
)

echo.
echo ===================================================
echo [3/3] 开始扫描 AAR 并同步到 FongMi 工程...
echo ===================================================

set found_count=0

:: 深度遍历源目录下的所有 release.aar 文件
for /r "%src%" %%a in (*-release.aar) do (
    :: 在 move.txt 中精确查找是否存在当前找到的 AAR 文件名
    findstr /i /c:"%%~nxa" "%txt%" >nul
    if not errorlevel 1 (
        :: 拷贝并覆盖
        copy /y "%%a" "%dst%\" >nul
        echo [OK] 已同步: %%~nxa
        set /a found_count+=1
    )
)

echo.
echo ===================================================
echo 任务完成！共成功同步了 %found_count% 个 AAR 文件。
echo ===================================================
pause