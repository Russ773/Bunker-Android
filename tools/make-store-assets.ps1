<#
  Builds the Google Play images from raw emulator screenshots.

    powershell -File tools\make-store-assets.ps1

  Input : store\raw\*.png   (full phone screenshots, e.g. 1080x2400, named 01-dashboard.png, 02-console.png ...)
  Output: store\play\screenshot-*.png   1080x1920 (16:9, Play allows up to 2:1) with a caption above the phone
          store\play\feature-graphic.png   1024x500
  The icon (512x512) is store\play-store-icon-512.png.
#>
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$rawDir = Join-Path $root 'store\raw'
$outDir = Join-Path $root 'store\play'
New-Item -ItemType Directory -Force $outDir | Out-Null

$bg1 = [System.Drawing.Color]::FromArgb(20, 22, 26)
$bg2 = [System.Drawing.Color]::FromArgb(28, 31, 38)
$accent = [System.Drawing.Color]::FromArgb(224, 89, 42)
$text = [System.Drawing.Color]::FromArgb(230, 232, 236)
$muted = [System.Drawing.Color]::FromArgb(138, 145, 156)

$captions = @{
  '01-dashboard' = @('Your whole server at a glance', 'Players, FPS and charts, live')
  '02-console'   = @('The live console', 'Search, filter and run any command')
  '03-players'   = @('Everyone online', 'Ping, time played and health')
  '04-actions'   = @('One tap to act', 'Kick, ban, mute, teleport, give items')
  '05-chat'      = @('Chat like the game', 'Name colours, team and plugin tags')
  '06-overview'  = @('All your servers', 'Status, players and FPS side by side')
  '07-plugins'   = @('Carbon and Oxide plugins', 'Sort, reload, load and unload')
}

function New-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = $r * 2
  $p.AddArc($x, $y, $d, $d, 180, 90)
  $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
  $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
  $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
  $p.CloseFigure()
  return $p
}

# Fonts have empty space before the first letter, and it differs per font and size. This draws the text so the
# first visible pixel of the first letter lands exactly on $inkX, so lines in different fonts line up.
function Draw-InkString($g, [string]$text, $font, $brush, [float]$inkX, [float]$y) {
  $size = $g.MeasureString($text, $font)
  $w = [int]($size.Width + 60); $h = [int]($size.Height + 20)
  $probe = New-Object System.Drawing.Bitmap($w, $h)
  $pg = [System.Drawing.Graphics]::FromImage($probe)
  $pg.Clear([System.Drawing.Color]::Black); $pg.TextRenderingHint = 'AntiAliasGridFit'
  $pg.DrawString($text, $font, [System.Drawing.Brushes]::White, 30, 5)
  $left = 30
  :scan for ($x = 0; $x -lt $w; $x++) { for ($yy = 0; $yy -lt $h; $yy++) { if ($probe.GetPixel($x, $yy).R -gt 90) { $left = $x; break scan } } }
  $pg.Dispose(); $probe.Dispose()
  $g.DrawString($text, $font, $brush, $inkX - ($left - 30), $y)
}

function New-Canvas([int]$w, [int]$h) {
  $bmp = New-Object System.Drawing.Bitmap($w, $h)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'; $g.TextRenderingHint = 'AntiAliasGridFit'
  $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(0, 0, $w, $h)), $bg1, $bg2, 65)
  $g.FillRectangle($brush, 0, 0, $w, $h)
  return @($bmp, $g)
}

function Draw-Phone($g, $img, [float]$x, [float]$y, [float]$h) {
  $w = $h * $img.Width / $img.Height
  $pad = 10
  $frame = New-RoundedPath ($x - $pad) ($y - $pad) ($w + 2 * $pad) ($h + 2 * $pad) 54
  $g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(11, 12, 15))), $frame)
  $g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(58, 63, 74), 3)), $frame)
  $clip = New-RoundedPath $x $y $w $h 44
  $state = $g.Save()
  $g.SetClip($clip)
  $g.DrawImage($img, $x, $y, $w, $h)
  $g.Restore($state)
  return $w
}

# ------------------------------------------------------------------ screenshots
if (Test-Path $rawDir) {
  Get-ChildItem $rawDir -Filter *.png | Sort-Object Name | ForEach-Object {
    $key = $_.BaseName
    $cap = $captions[$key]
    if (-not $cap) { $cap = @($key, '') }
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    $canvas = New-Canvas 1080 1920
    $bmp = $canvas[0]; $g = $canvas[1]

    $titleFont = New-Object System.Drawing.Font('Segoe UI Semibold', 54, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $subFont = New-Object System.Drawing.Font('Segoe UI', 32, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat; $fmt.Alignment = 'Center'
    $g.DrawString($cap[0], $titleFont, (New-Object System.Drawing.SolidBrush($text)), (New-Object System.Drawing.RectangleF(40, 60, 1000, 80)), $fmt)
    $g.DrawString($cap[1], $subFont, (New-Object System.Drawing.SolidBrush($muted)), (New-Object System.Drawing.RectangleF(40, 138, 1000, 50)), $fmt)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush($accent)), 490, 200, 100, 6)

    $phoneH = 1620
    $phoneW = $phoneH * $img.Width / $img.Height
    [void](Draw-Phone $g $img ((1080 - $phoneW) / 2) 250 $phoneH)

    $out = Join-Path $outDir ("screenshot-" + $key + ".png")
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose(); $img.Dispose()
    "wrote $out"
  }
}

# ------------------------------------------------------------------ feature graphic 1024x500
$logo = [System.Drawing.Image]::FromFile((Join-Path $root 'store\play-store-icon-512.png'))
$canvas = New-Canvas 1024 500
$bmp = $canvas[0]; $g = $canvas[1]
$g.DrawImage($logo, (64 - 150 * 82 / 512), 70, 150, 150)   # the hexagon starts 82/512 into the icon
$big = New-Object System.Drawing.Font('Segoe UI Black', 84, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$mid = New-Object System.Drawing.Font('Segoe UI', 30, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$small = New-Object System.Drawing.Font('Segoe UI', 24, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
Draw-InkString $g 'BUNKER' $big (New-Object System.Drawing.SolidBrush($text)) 64 228
Draw-InkString $g 'RUST SERVER ADMIN' $mid (New-Object System.Drawing.SolidBrush($accent)) 64 326
$dot = [string][char]0x00B7
$tagline = "Console  $dot  Players  $dot  Chat  $dot  Moderation"
Draw-InkString $g $tagline $small (New-Object System.Drawing.SolidBrush($muted)) 64 384

$shot = Get-ChildItem $rawDir -Filter '01-*.png' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($shot) {
  $img = [System.Drawing.Image]::FromFile($shot.FullName)
  $h = 760  # taller than the canvas, so only the top of the phone shows
  [void](Draw-Phone $g $img 640 70 $h)
  $img.Dispose()
}
$out = Join-Path $outDir 'feature-graphic.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $logo.Dispose()
"wrote $out"

# ------------------------------------------------------------------ GitHub social preview 1280x640
# (Upload it in the repo's Settings > General > Social preview. GitHub has no command line for this.)
$logo = [System.Drawing.Image]::FromFile((Join-Path $root 'store\play-store-icon-512.png'))
$canvas = New-Canvas 1280 640
$bmp = $canvas[0]; $g = $canvas[1]
$g.DrawImage($logo, (88 - 190 * 82 / 512), 100, 190, 190)
$big = New-Object System.Drawing.Font('Segoe UI Black', 112, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$mid = New-Object System.Drawing.Font('Segoe UI', 40, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$small = New-Object System.Drawing.Font('Segoe UI', 30, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
Draw-InkString $g 'BUNKER' $big (New-Object System.Drawing.SolidBrush($text)) 88 290
Draw-InkString $g ("RUST SERVER ADMIN  $dot  ANDROID") $mid (New-Object System.Drawing.SolidBrush($accent)) 88 424
Draw-InkString $g "Console  $dot  Players  $dot  Chat  $dot  Moderation" $small (New-Object System.Drawing.SolidBrush($muted)) 88 494
$shot = Get-ChildItem $rawDir -Filter '01-*.png' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($shot) {
  $img = [System.Drawing.Image]::FromFile($shot.FullName)
  [void](Draw-Phone $g $img 820 96 960)
  $img.Dispose()
}
$out = Join-Path $root 'store\social-preview.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $logo.Dispose()
"wrote $out"
