@echo off
title GBA Native Web Tester
echo ====================================================
echo      GBA NATIVE WEB TESTER (WASM + HTML5)
echo ====================================================
echo.
echo [1] Khoi tao local web server tai cong 8080...
echo [2] Dang mo trinh duyet http://localhost:8080/web/ ...
echo.
echo * Luu y: Giu cua so nay mo trong luc test web.
echo   Bam Ctrl + C de dung server.
echo ====================================================
echo.

start "" "http://localhost:8080/web/"
python -m http.server 8080
