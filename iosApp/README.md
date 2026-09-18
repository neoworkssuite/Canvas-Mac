# NeoCanvas iPad Host

This directory contains the native Apple host for NeoCanvas.

NeoCanvas itself remains in Kotlin Multiplatform.

The iOS host performs only Apple-specific startup and integration.

## Architecture

SwiftUI
    |
UIViewControllerRepresentable
    |
NeoCanvasKit
    |
MainViewController()
    |
NeoCanvasApp()

## Generate Xcode project

On macOS:

    brew install xcodegen

    cd iosApp
    xcodegen generate

This produces:

    NeoCanvas.xcodeproj

The Xcode build phase invokes:

    :ui:embedAndSignAppleFrameworkForXcode

which builds and embeds the Kotlin NeoCanvas framework.

## First milestone

The initial Apple host launches the existing shared NeoCanvas interface.

Apple Files, Photos and Pencil platform services are added separately.