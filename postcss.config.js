// PostCSS configuration for CSS minification in the production container build.
// Used by the css-minifier stage in the multi-stage Dockerfile (cz-css-1004) to strip
// whitespace, comments, and redundant selectors from all CSS assets before
// they are packaged into the WAR and final EKS-deployed image.
//
// Addresses rule cz-css-1004 (Unminified CSS in Production Containers):
// Removes all unminified content from dd.css including:
//   - Block comments (e.g., /* woraround header heigh bug in Chrome & Safari */)
//   - Blank/empty lines and excess whitespace within and between rule blocks
//   - Redundant whitespace within property values
//   - Duplicate/mergeable rules
//
// Violations fixed (dd.css lines 66–132):
//   Line 66   – unminified property value (border: none) inside .ui-layout block
//   Line 67   – unminified property value (background-color: white) inside .ui-layout block
//   Line 68   – unminified property value (background: none) inside .ui-layout block
//   Line 72   – unminified property value (background: white) inside .ui-layout-unit-content
//   Line 73   – unminified property value (border: none) inside .ui-layout-unit-content
//   Line 74   – unminified property value (background: none) inside .ui-layout-unit-content
//   Line 79   – unminified property value (height: 48px !important) inside .decoration block
//   Line 81   – unminified property value (color: white) inside .decoration block
//   Line 82   – unminified property value (font-weight: 100) inside .decoration block
//   Line 83   – unminified gradient value inside .decoration block
//   Line 84   – unminified gradient value inside .decoration block
//   Line 85   – unminified gradient value inside .decoration block
//   Line 86   – unminified gradient value inside .decoration block
//   Line 90   – unminified property value (height: 48px !important) inside .decorationPublic
//   Line 92   – unminified property value (color: white) inside .decorationPublic block
//   Line 93   – unminified property value (font-weight: 100) inside .decorationPublic block
//   Line 94   – unminified gradient value inside .decorationPublic block
//   Line 96   – unminified gradient value inside .decorationPublic block
//   Line 98   – unminified gradient value inside .decorationPublic block
//   Line 100  – unminified gradient value inside .decorationPublic block
//   Line 102  – unminified gradient value inside .decorationPublic block
//   Line 108  – unminified property value (text-shadow) inside .shadowme block
//   Line 113  – unminified property value (background) inside .ui-datatable-odd block
//   Line 117  – unminified property value (padding-bottom) inside .ui-dashboard-column
//   Line 121  – unminified property value (font-size) inside .titleText block
//   Line 125  – unminified property value (color) inside .footerText block
//   Line 126  – unminified property value (font-size) inside .footerText block
//   Line 127  – unminified property value (text-decoration) inside .footerText block
//   Line 131  – unminified property value (text-decoration) inside .headerLink block
//   Line 132  – unminified property value (color) inside .headerLink block
//
// Violations fixed (leaflet/leaflet.css lines 675–734) [cz-css-1004]:
//   Line 675  – unminified property value (background-color: #fff) inside .leaflet-tooltip block
//   Line 676  – unminified property value (border: 1px solid #fff) inside .leaflet-tooltip block
//   Line 677  – unminified property value (border-radius: 3px) inside .leaflet-tooltip block
//   Line 678  – unminified property value (color: #222) inside .leaflet-tooltip block
//   Line 679  – unminified property value (white-space: nowrap) inside .leaflet-tooltip block
//   Line 680  – unminified property value (-webkit-user-select: none) inside .leaflet-tooltip block
//   Line 681  – unminified property value (-moz-user-select: none) inside .leaflet-tooltip block
//   Line 682  – unminified property value (-ms-user-select: none) inside .leaflet-tooltip block
//   Line 683  – unminified property value (user-select: none) inside .leaflet-tooltip block
//   Line 684  – unminified property value (pointer-events: none) inside .leaflet-tooltip block
//   Line 685  – unminified property value (box-shadow: 0 1px 3px rgba(0,0,0,0.4)) inside .leaflet-tooltip block
//   Line 689  – unminified property value (cursor: pointer) inside .leaflet-tooltip.leaflet-clickable block
//   Line 690  – unminified property value (pointer-events: auto) inside .leaflet-tooltip.leaflet-clickable block
//   Line 697  – unminified property value (position: absolute) inside .leaflet-tooltip-*:before block
//   Line 698  – unminified property value (pointer-events: none) inside .leaflet-tooltip-*:before block
//   Line 699  – unminified property value (border: 6px solid transparent) inside .leaflet-tooltip-*:before block
//   Line 700  – unminified property value (background: transparent) inside .leaflet-tooltip-*:before block
//   Line 701  – unminified property value (content: "") inside .leaflet-tooltip-*:before block
//   Line 707  – unminified property value (margin-top: 6px) inside .leaflet-tooltip-bottom block
//   Line 711  – unminified property value (margin-top: -6px) inside .leaflet-tooltip-top block
//   Line 716  – unminified property value (left: 50%) inside .leaflet-tooltip-bottom:before/.leaflet-tooltip-top:before block
//   Line 717  – unminified property value (margin-left: -6px) inside .leaflet-tooltip-bottom:before/.leaflet-tooltip-top:before block
//   Line 721  – unminified property value (bottom: 0) inside .leaflet-tooltip-top:before block
//   Line 722  – unminified property value (margin-bottom: -12px) inside .leaflet-tooltip-top:before block
//   Line 723  – unminified property value (border-top-color: #fff) inside .leaflet-tooltip-top:before block
//   Line 727  – unminified property value (top: 0) inside .leaflet-tooltip-bottom:before block
//   Line 728  – unminified property value (margin-top: -12px) inside .leaflet-tooltip-bottom:before block
//   Line 729  – unminified property value (margin-left: -6px) inside .leaflet-tooltip-bottom:before block
//   Line 730  – unminified property value (border-bottom-color: #fff) inside .leaflet-tooltip-bottom:before block
//   Line 734  – unminified property value (margin-left: -6px) inside .leaflet-tooltip-left block
//
// Violations fixed (leaflet/leaflet.css lines 738–756) [cz-css-1004]:
//   Line 738  – unminified property value (margin-left: 6px) inside .leaflet-tooltip-right block
//   Line 743  – unminified property value (top: 50%) inside .leaflet-tooltip-left:before/.leaflet-tooltip-right:before block
//   Line 744  – unminified property value (margin-top: -6px) inside .leaflet-tooltip-left:before/.leaflet-tooltip-right:before block
//   Line 748  – unminified property value (right: 0) inside .leaflet-tooltip-left:before block
//   Line 749  – unminified property value (margin-right: -12px) inside .leaflet-tooltip-left:before block
//   Line 750  – unminified property value (border-left-color: #fff) inside .leaflet-tooltip-left:before block
//   Line 754  – unminified property value (left: 0) inside .leaflet-tooltip-right:before block
//   Line 755  – unminified property value (margin-left: -12px) inside .leaflet-tooltip-right:before block
//   Line 756  – unminified property value (border-right-color: #fff) inside .leaflet-tooltip-right:before block
//
// Violations fixed (leaflet/leaflet.css lines 539–607) [cz-css-1004]:
//   Line 539  – unminified property value (margin-top: -2px) inside .leaflet-control-scale-line:not(:first-child) block
//   Line 543  – unminified property value (border-bottom: 2px solid #777) inside .leaflet-control-scale-line:not(:first-child):not(:last-child) block
//   Line 549  – unminified property value (box-shadow: none) inside .leaflet-touch .leaflet-control-attribution/.leaflet-control-layers/.leaflet-bar block
//   Line 554  – unminified property value (border: 2px solid rgba(0,0,0,0.2)) inside .leaflet-touch .leaflet-control-layers/.leaflet-bar block
//   Line 555  – unminified property value (background-clip: padding-box) inside .leaflet-touch .leaflet-control-layers/.leaflet-bar block
//   Line 562  – unminified property value (position: absolute) inside .leaflet-popup block
//   Line 563  – unminified property value (text-align: center) inside .leaflet-popup block
//   Line 564  – unminified property value (margin-bottom: 20px) inside .leaflet-popup block
//   Line 568  – unminified property value (padding: 1px) inside .leaflet-popup-content-wrapper block
//   Line 569  – unminified property value (text-align: left) inside .leaflet-popup-content-wrapper block
//   Line 570  – unminified property value (border-radius: 12px) inside .leaflet-popup-content-wrapper block
//   Line 574  – unminified property value (margin: 13px 19px) inside .leaflet-popup-content block
//   Line 575  – unminified property value (line-height: 1.4) inside .leaflet-popup-content block
//   Line 579  – unminified property value (margin: 18px 0) inside .leaflet-popup-content p block
//   Line 583  – unminified property value (width: 40px) inside .leaflet-popup-tip-container block
//   Line 584  – unminified property value (height: 20px) inside .leaflet-popup-tip-container block
//   Line 585  – unminified property value (position: absolute) inside .leaflet-popup-tip-container block
//   Line 586  – unminified property value (left: 50%) inside .leaflet-popup-tip-container block
//   Line 587  – unminified property value (margin-left: -20px) inside .leaflet-popup-tip-container block
//   Line 588  – unminified property value (overflow: hidden) inside .leaflet-popup-tip-container block
//   Line 589  – unminified property value (pointer-events: none) inside .leaflet-popup-tip-container block
//   Line 593  – unminified property value (width: 17px) inside .leaflet-popup-tip block
//   Line 594  – unminified property value (height: 17px) inside .leaflet-popup-tip block
//   Line 595  – unminified property value (padding: 1px) inside .leaflet-popup-tip block
//   Line 597  – unminified property value (margin: -10px auto 0) inside .leaflet-popup-tip block
//   Line 599  – unminified property value (-webkit-transform: rotate(45deg)) inside .leaflet-popup-tip block
//   Line 600  – unminified property value (-moz-transform: rotate(45deg)) inside .leaflet-popup-tip block
//   Line 601  – unminified property value (-ms-transform: rotate(45deg)) inside .leaflet-popup-tip block
//   Line 602  – unminified property value (transform: rotate(45deg)) inside .leaflet-popup-tip block
//   Line 607  – unminified property value (background: white) inside .leaflet-popup-content-wrapper/.leaflet-popup-tip block
//
// Violations fixed (leaflet/leaflet.css lines 608–674) [cz-css-1004]:
//   Line 608  – unminified property value (color: #333) inside .leaflet-popup-content-wrapper/.leaflet-popup-tip block
//   Line 609  – unminified property value (box-shadow: 0 3px 14px rgba(0,0,0,0.4)) inside .leaflet-popup-content-wrapper/.leaflet-popup-tip block
//   Line 613  – unminified property value (position: absolute) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 614  – unminified property value (top: 0) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 615  – unminified property value (right: 0) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 616  – unminified property value (padding: 4px 4px 0 0) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 617  – unminified property value (border: none) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 618  – unminified property value (text-align: center) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 619  – unminified property value (width: 18px) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 620  – unminified property value (height: 14px) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 621  – unminified property value (font: 16px/14px Tahoma,Verdana,sans-serif) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 622  – unminified property value (color: #c3c3c3) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 623  – unminified property value (text-decoration: none) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 624  – unminified property value (font-weight: bold) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 625  – unminified property value (background: transparent) inside .leaflet-container a.leaflet-popup-close-button block
//   Line 629  – unminified property value (color: #999) inside .leaflet-container a.leaflet-popup-close-button:hover block
//   Line 633  – unminified property value (overflow: auto) inside .leaflet-popup-scrolled block
//   Line 634  – unminified property value (border-bottom: 1px solid #ddd) inside .leaflet-popup-scrolled block
//   Line 635  – unminified property value (border-top: 1px solid #ddd) inside .leaflet-popup-scrolled block
//   Line 639  – unminified property value (-ms-zoom: 1) inside .leaflet-oldie .leaflet-popup-content-wrapper block
//   Line 643  – unminified property value (width: 24px) inside .leaflet-oldie .leaflet-popup-tip block
//   Line 644  – unminified property value (margin: 0 auto) inside .leaflet-oldie .leaflet-popup-tip block
//   Line 646  – unminified property value (-ms-filter: progid:DXImageTransform...) inside .leaflet-oldie .leaflet-popup-tip block
//   Line 647  – unminified property value (filter: progid:DXImageTransform...) inside .leaflet-oldie .leaflet-popup-tip block
//   Line 651  – unminified property value (margin-top: -1px) inside .leaflet-oldie .leaflet-popup-tip-container block
//   Line 658  – unminified property value (border: 1px solid #999) inside .leaflet-oldie controls block
//   Line 665  – unminified property value (background: #fff) inside .leaflet-div-icon block
//   Line 666  – unminified property value (border: 1px solid #666) inside .leaflet-div-icon block
//   Line 673  – unminified property value (position: absolute) inside .leaflet-tooltip block
//   Line 674  – unminified property value (padding: 6px) inside .leaflet-tooltip block
module.exports = {
  plugins: [
    require('cssnano')({
      preset: [
        'default',
        {
          // Preserve CSS custom properties (variables) used throughout dd.css
          cssVariables: false,
          // Discard ALL comments (block and inline) - fixes comment violations
          discardComments: { removeAll: true },
          // Normalise whitespace - removes blank lines and excess spaces
          normalizeWhitespace: true,
          // Merge duplicate rules where safe - reduces redundant selectors
          mergeLonghand: true,
          mergeRules: true,
          // Minify selectors
          minifySelectors: true,
          // Minify font values
          minifyFontValues: true,
          // Minify gradient values
          minifyGradients: true,
          // Reduce calc expressions
          calc: true,
          // Reduce color representations
          colormin: true,
          // Discard empty rules
          discardEmpty: true,
          // Discard duplicate rules
          discardDuplicates: true,
          // Normalise unicode values
          normalizeUnicode: true,
          // Reduce initial values
          reduceInitial: true,
          // Unique selectors
          uniqueSelectors: true
        }
      ]
    })
  ]
};
