param([string]$Source = "$PSScriptRoot\..\assets\branding\neocanvas-source.png")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = [IO.Path]::GetFullPath("$PSScriptRoot\..")
$original = [Drawing.Image]::FromFile([IO.Path]::GetFullPath($Source))
function Export-Png([int]$Size, [string]$RelativePath, [double]$ContentScale = 1) {
    $target = Join-Path $projectRoot $RelativePath
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
    $bitmap = [Drawing.Bitmap]::new($Size, $Size)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([Drawing.Color]::Black)
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $side = [int]($Size * $ContentScale)
        $offset = [int](($Size - $side) / 2)
        $graphics.DrawImage($original, $offset, $offset, $side, $side)
        $bitmap.Save($target, [Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}
try {
    Export-Png 256 'ui/src/commonMain/composeResources/drawable/neocanvas_logo.png'
    $densities = @{ mdpi=48; hdpi=72; xhdpi=96; xxhdpi=144; xxxhdpi=192 }
    foreach ($density in $densities.Keys) {
        Export-Png $densities[$density] "androidApp/src/main/res/mipmap-$density/ic_launcher.png"
    }
    Export-Png 432 'androidApp/src/main/res/drawable-nodpi/ic_launcher_foreground.png' .62
    $sizes = @(16, 24, 32, 48, 64, 128, 256)
    $images = @()
    foreach ($size in $sizes) {
        $relative = "assets/branding/windows/icon-$size.png"
        Export-Png $size $relative
        $images += ,([IO.File]::ReadAllBytes((Join-Path $projectRoot $relative)))
    }
    $stream = [IO.File]::Create((Join-Path $projectRoot 'assets/branding/neocanvas.ico'))
    $writer = [IO.BinaryWriter]::new($stream)
    try {
        $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$sizes.Count)
        $position = 6 + 16 * $sizes.Count
        for ($i = 0; $i -lt $sizes.Count; $i++) {
            $dimension = if ($sizes[$i] -eq 256) { 0 } else { $sizes[$i] }
            $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
            $writer.Write([byte]0); $writer.Write([byte]0)
            $writer.Write([uint16]1); $writer.Write([uint16]32)
            $writer.Write([uint32]$images[$i].Length); $writer.Write([uint32]$position)
            $position += $images[$i].Length
        }
        foreach ($bytes in $images) { $writer.Write([byte[]]$bytes) }
    } finally { $writer.Dispose() }
} finally { $original.Dispose() }
Write-Output 'Generated shared logo, Android launcher images, and multi-resolution Windows ICO.'
