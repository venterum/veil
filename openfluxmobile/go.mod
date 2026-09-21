module openfluxmobile

go 1.26.4

require universal-bypass-tool v0.0.0

// Only needed for standalone builds/IDE; the combined libv2ray module supplies
// its own replace directive when it binds this package.
replace universal-bypass-tool => ../OpenFlux
