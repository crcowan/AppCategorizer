param(
    [string]$SourcePath,
    [string]$ResDir
)

Add-Type -AssemblyName System.Drawing

$img = [System.Drawing.Image]::FromFile($SourcePath)

# Standard Icon Sizes
$sizes = @{
    "mdpi" = 48
    "hdpi" = 72
    "xhdpi" = 96
    "xxhdpi" = 144
    "xxxhdpi" = 192
}

# Adaptive Icon Foreground Sizes
$adaptiveSizes = @{
    "mdpi" = 108
    "hdpi" = 162
    "xhdpi" = 216
    "xxhdpi" = 324
    "xxxhdpi" = 432
}

function Resize-Image($size, $path) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($img, 0, 0, $size, $size)
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

foreach ($density in $sizes.Keys) {
    $dir = Join-Path $ResDir "mipmap-$density"
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    
    $outPath = Join-Path $dir "ic_launcher.png"
    Resize-Image $sizes[$density] $outPath
    Write-Host "Created $outPath"
}

foreach ($density in $adaptiveSizes.Keys) {
    $dir = Join-Path $ResDir "mipmap-$density"
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    
    $outPath = Join-Path $dir "ic_launcher_foreground.png"
    Resize-Image $adaptiveSizes[$density] $outPath
    Write-Host "Created $outPath"
}

# Create a background color for adaptive icon
$bgBmp = New-Object System.Drawing.Bitmap(1, 1)
$bgG = [System.Drawing.Graphics]::FromImage($bgBmp)
$bgG.Clear([System.Drawing.Color]::White)
$bgG.Dispose()

foreach ($density in $adaptiveSizes.Keys) {
    $dir = Join-Path $ResDir "mipmap-$density"
    $outPath = Join-Path $dir "ic_launcher_background.png"
    $bgBmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
}
$bgBmp.Dispose()

$img.Dispose()
