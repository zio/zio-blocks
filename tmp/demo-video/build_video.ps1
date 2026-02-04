$ErrorActionPreference = "Stop"

Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)

python .\render_slides.py

$filter = "[0:v]fps=30,format=rgba,setsar=1[v0];" +
          "[1:v]fps=30,format=rgba,setsar=1[v1];" +
          "[2:v]fps=30,format=rgba,setsar=1[v2];" +
          "[3:v]fps=30,format=rgba,setsar=1[v3];" +
          "[4:v]fps=30,format=rgba,setsar=1[v4];" +
          "[5:v]fps=30,format=rgba,setsar=1[v5];" +
          "[v0][v1]xfade=transition=fade:duration=0.7:offset=5.3[v01];" +
          "[v01][v2]xfade=transition=fade:duration=0.7:offset=18.6[v02];" +
          "[v02][v3]xfade=transition=fade:duration=0.7:offset=31.9[v03];" +
          "[v03][v4]xfade=transition=fade:duration=0.7:offset=45.2[v04];" +
          "[v04][v5]xfade=transition=fade:duration=0.7:offset=62.5," +
          "subtitles=captions.srt:force_style='FontName=Cascadia Code,FontSize=26,PrimaryColour=&H00FFFFFF,OutlineColour=&H00111111,Outline=2,Shadow=0,BorderStyle=1,Alignment=2,MarginV=44'[v]"

ffmpeg -y `
  -loop 1 -t 6  -i 01_title.png `
  -loop 1 -t 14 -i 02_selectors.png `
  -loop 1 -t 14 -i 03_serializable.png `
  -loop 1 -t 14 -i 04_validation.png `
  -loop 1 -t 18 -i 05_demo.png `
  -loop 1 -t 10 -i 06_tests.png `
  -filter_complex $filter `
  -map "[v]" -an `
  -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -movflags +faststart `
  migration-demo.mp4

Write-Host "Built: $((Resolve-Path .\\migration-demo.mp4).Path)"
