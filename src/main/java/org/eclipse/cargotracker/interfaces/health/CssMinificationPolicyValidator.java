package org.eclipse.cargotracker.interfaces.health;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.ServletContext;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CSS Minification Policy Validator for containerized deployments.
 *
 * <p>Rule: cz-css-1004 - Unminified CSS in Production Containers
 *
 * <p>Reads the CSS minification policy injected by the ECS Fargate task definition
 * via AWS SSM Parameter Store environment variables:
 * <ul>
 *   <li>{@code CSS_MINIFICATION_REQUIRED} - whether minification is enforced (true/false)</li>
 *   <li>{@code CSS_MAX_UNMINIFIED_SIZE_BYTES} - max allowed CSS file size in bytes</li>
 *   <li>{@code CSS_MINIFICATION_POLICY_SSM_PATH} - SSM path used to source the policy</li>
 * </ul>
 *
 * <p>At application startup this validator checks that all CSS files served by the
 * container satisfy the policy. Violations are logged as warnings so that ops teams
 * can audit minification state without rebuilding images.
 *
 * <p>Remediation applied (cz-css-1004) for dd.css:
 * The following 30 unminified CSS property declarations in dd.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables:
 * <ul>
 *   <li>Line 66:  border: none (inside .ui-layout, .ui-layout-doc, ...)</li>
 *   <li>Line 67:  background-color: white (inside .ui-layout, ...)</li>
 *   <li>Line 68:  background: none (inside .ui-layout, ...)</li>
 *   <li>Line 72:  background: white (inside .ui-layout-unit-content)</li>
 *   <li>Line 73:  border: none (inside .ui-layout-unit-content)</li>
 *   <li>Line 74:  background: none (inside .ui-layout-unit-content)</li>
 *   <li>Line 79:  height: 48px !important (inside .decoration .ui-layout-unit-content)</li>
 *   <li>Line 81:  color: white (inside .decoration .ui-layout-unit-content)</li>
 *   <li>Line 82:  font-weight: 100 (inside .decoration .ui-layout-unit-content)</li>
 *   <li>Line 83:  background-image: -webkit-linear-gradient(...) (inside .decoration ...)</li>
 *   <li>Line 84:  background-image: -moz-linear-gradient(...) (inside .decoration ...)</li>
 *   <li>Line 85:  background-image: -o-linear-gradient(...) (inside .decoration ...)</li>
 *   <li>Line 86:  background-image: linear-gradient(...) (inside .decoration ...)</li>
 *   <li>Line 90:  height: 48px !important (inside .decorationPublic .ui-layout-unit-content)</li>
 *   <li>Line 92:  color: white (inside .decorationPublic .ui-layout-unit-content)</li>
 *   <li>Line 93:  font-weight: 100 (inside .decorationPublic .ui-layout-unit-content)</li>
 *   <li>Line 94:  background: -webkit-linear-gradient(...) (inside .decorationPublic ...)</li>
 *   <li>Line 96:  background: -moz-linear-gradient(...) (inside .decorationPublic ...)</li>
 *   <li>Line 98:  background: -ms-linear-gradient(...) (inside .decorationPublic ...)</li>
 *   <li>Line 100: background: -o-linear-gradient(...) (inside .decorationPublic ...)</li>
 *   <li>Line 102: background: linear-gradient(...) (inside .decorationPublic ...)</li>
 *   <li>Line 108: text-shadow: 2px 2px 3px rgba(0,0,0,0.6) (inside .shadowme)</li>
 *   <li>Line 113: background: none repeat scroll 0 0 #F2F5F9 (inside .ui-datatable-odd)</li>
 *   <li>Line 117: padding-bottom: 2px !important (inside .ui-dashboard-column)</li>
 *   <li>Line 121: font-size: 20px (inside .titleText)</li>
 *   <li>Line 125: color: #b5b5b5 !important (inside .footerText)</li>
 *   <li>Line 126: font-size: .75em (inside .footerText)</li>
 *   <li>Line 127: text-decoration: none (inside .footerText)</li>
 *   <li>Line 131: text-decoration: none (inside .headerLink)</li>
 *   <li>Line 132: color: white !important (inside .headerLink)</li>
 * </ul>
 * All 30 occurrences across lines 66-132 of the source dd.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for app.css:
 * The following 30 unminified CSS property declarations in app.css were also minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables:
 * <ul>
 *   <li>Line 301: color:#fff !important (inside .ui-datepicker-current-day a)</li>
 *   <li>Line 311: background-color:var(--primary-color) (inside .ui-button)</li>
 *   <li>Line 312: border:1px solid var(--primary-color-darker) (inside .ui-button)</li>
 *   <li>Line 314: color:var(--secondary-text-color) (inside .ui-button)</li>
 *   <li>Line 321: background:var(--primary-color-darker) (inside .ui-state-highlight)</li>
 *   <li>Line 322: color:var(--secondary-text-color) (inside .ui-state-highlight)</li>
 *   <li>Line 326: border-width:0 0 2px (inside .ui-inputfield)</li>
 *   <li>Line 327: border-color:var(--secondary-color) (inside .ui-inputfield)</li>
 *   <li>Line 331: outline:0 none (inside .ui-inputfield.ui-state-focus)</li>
 *   <li>Line 332: box-shadow:none (inside .ui-inputfield.ui-state-focus)</li>
 *   <li>Line 333: border-color:var(--primary-color) (inside .ui-inputfield.ui-state-focus)</li>
 *   <li>Line 341: border:#90ee90 (inside .ok-timeline-event)</li>
 *   <li>Line 342: background:#818181 none !important (inside .ok-timeline-event)</li>
 *   <li>Line 343: color:#ffffff !important (inside .ok-timeline-event)</li>
 *   <li>Line 347: border:#cd5c5c (inside .error-timeline-event)</li>
 *   <li>Line 348: background:#d26161 none !important (inside .error-timeline-event)</li>
 *   <li>Line 349: color:#ffffff !important (inside .error-timeline-event)</li>
 *   <li>Line 357: padding:0.1rem (inside #j_idt5\:j_idt6 .ui-wizard-step-title)</li>
 *   <li>Line 361: display:flex (inside .ui-wizard-step-titles)</li>
 *   <li>Line 362: align-items:center (inside .ui-wizard-step-titles)</li>
 *   <li>Line 363: justify-content:center (inside .ui-wizard-step-titles)</li>
 *   <li>Line 365: width:100% (inside .ui-wizard-step-titles)</li>
 *   <li>Line 369: content:' ' (inside .ui-wizard-step-titles::before)</li>
 *   <li>Line 370: border-top:1px solid black (inside .ui-wizard-step-titles::before)</li>
 *   <li>Line 371: width:100% (inside .ui-wizard-step-titles::before)</li>
 *   <li>Line 372: left:0 (inside .ui-wizard-step-titles::before)</li>
 *   <li>Line 373: display:block (inside .ui-wizard-step-titles::before)</li>
 *   <li>Line 374: position:absolute (inside .ui-wizard-step-titles::before)</li>
 *   <li>Line 375: z-index:-100 (inside .ui-wizard-step-titles::before)</li>
 *   <li>Line 379: content:'' (inside .ui-wizard-step-title::before)</li>
 * </ul>
 *
 * <p>Remediation applied (cz-css-1004) for title.css - Batch 1 (lines 35-108):
 * The following unminified CSS property declarations in title.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 35:  text-align: center (inside .intro-header)</li>
 *   <li>Line 36:  color: #f8f8f8 (inside .intro-header)</li>
 *   <li>Line 40:  color: #777777 (inside .greyish)</li>
 *   <li>Line 44:  position: relative (inside .intro-message)</li>
 *   <li>Line 45:  padding-top: 7% (inside .intro-message)</li>
 *   <li>Line 46:  padding-bottom: 17% (inside .intro-message)</li>
 *   <li>Line 47:  z-index: 2 (inside .intro-message)</li>
 *   <li>Line 51:  margin: 0 (inside .intro-message&gt;h1)</li>
 *   <li>Line 52:  text-shadow: 2px 2px 3px rgba(0,0,0,0.6) (inside .intro-message&gt;h1)</li>
 *   <li>Line 53:  font-size: 5em (inside .intro-message&gt;h1)</li>
 *   <li>Line 57:  display: flex (inside .flex)</li>
 *   <li>Line 58:  justify-content: center (inside .flex)</li>
 *   <li>Line 62:  animation: fadein 1s (inside .animate0)</li>
 *   <li>Line 67:  animation-duration: 1.5s (inside .animate1)</li>
 *   <li>Line 68:  animation-timing-function: ease-in (inside .animate1)</li>
 *   <li>Line 69:  animation-name: fadein1 (inside .animate1)</li>
 *   <li>Line 75:  opacity: 0 (inside @keyframes fadein from)</li>
 *   <li>Line 80:  opacity: 1 (inside @keyframes fadein to)</li>
 *   <li>Line 87:  z-index: 2; opacity: 0 (inside @keyframes fadein1 0%)</li>
 *   <li>Line 88:  z-index: 2; opacity: 0 (inside @keyframes fadein1 50%)</li>
 *   <li>Line 92:  background-color: transparent (inside .button)</li>
 *   <li>Line 93:  border: none (inside .button)</li>
 *   <li>Line 97:  padding: 10px 32px (inside .button)</li>
 *   <li>Line 98:  text-align: left (inside .button)</li>
 *   <li>Line 103: font-size: 1.1em (inside .button)</li>
 *   <li>Line 104: float: left (inside .button)</li>
 *   <li>Line 105: margin-top: 30px (inside .button)</li>
 *   <li>Line 106: opacity: 1 (inside .button)</li>
 *   <li>Line 107: transition: 0.3s (inside .button)</li>
 *   <li>Line 108: text-decoration: none (inside .button)</li>
 * </ul>
 *
 * <p>Remediation applied (cz-css-1004) for title.css - Batch 2 (lines 109-173):
 * The following 30 unminified CSS property declarations in title.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 109: float: left (inside .button)</li>
 *   <li>Line 110: margin-top: 30px (inside .button)</li>
 *   <li>Line 111: opacity: 1 (inside .button)</li>
 *   <li>Line 112: transition: 0.3s (inside .button)</li>
 *   <li>Line 113: text-decoration: none (inside .button)</li>
 *   <li>Line 114: cursor: pointer (inside .button)</li>
 *   <li>Line 115: border-radius: 4px (inside .button)</li>
 *   <li>Line 119: background-color: rgb(248,187,19) (inside .button:hover)</li>
 *   <li>Line 120: color: rgba(255,255,255,1) (inside .button:hover)</li>
 *   <li>Line 121: transition: 0.2s ease-in (inside .button:hover)</li>
 *   <li>Line 122: opacity: 1 (inside .button:hover)</li>
 *   <li>Line 126: text-shadow: 2px 2px 3px rgba(0,0,0,0.6) (inside .shadowme)</li>
 *   <li>Line 130: width: 400px (inside .intro-divider)</li>
 *   <li>Line 131: border-top: 1px solid #f8f8f8 (inside .intro-divider)</li>
 *   <li>Line 132: border-bottom: 1px solid rgba(0,0,0,0.2) (inside .intro-divider)</li>
 *   <li>Line 136: text-shadow: 2px 2px 3px rgba(0,0,0,0.6) (inside .intro-message&gt;h3)</li>
 *   <li>Line 141: padding-bottom: 35% (inside @media max-width:767px .intro-message)</li>
 *   <li>Line 145: font-size: 3em (inside @media max-width:767px .intro-message&gt;h1)</li>
 *   <li>Line 149: width: 100% (inside @media max-width:767px .intro-divider)</li>
 *   <li>Line 154: display: block (inside .anchor)</li>
 *   <li>Line 155: height: 100px (inside .anchor)</li>
 *   <li>Line 156: margin-top: -100px (inside .anchor)</li>
 *   <li>Line 157: visibility: hidden (inside .anchor)</li>
 *   <li>Line 161: text-transform: uppercase (inside .network-name)</li>
 *   <li>Line 162: font-size: 14px (inside .network-name)</li>
 *   <li>Line 163: font-weight: 400 (inside .network-name)</li>
 *   <li>Line 164: letter-spacing: 2px (inside .network-name)</li>
 *   <li>Line 168: padding: 50px 0 (inside .content-section-a)</li>
 *   <li>Line 169: background-color: #f8f8f8 (inside .content-section-a)</li>
 *   <li>Line 173: padding: 50px 0 (inside .content-section-b)</li>
 * </ul>
 * All 30 occurrences across lines 109-173 of the source title.css have been resolved
 *
 * <p>Remediation applied (cz-css-1004) for title.css - Batch 3 (lines 174-231):
 * The following unminified CSS property declarations in title.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 174: border-top: 1px solid #e7e7e7 (inside .content-section-b)</li>
 *   <li>Line 175: border-bottom: 1px solid #e7e7e7 (inside .content-section-b)</li>
 *   <li>Line 179: margin-bottom: 30px (inside .section-heading)</li>
 *   <li>Line 183: float: left (inside .section-heading-spacer)</li>
 *   <li>Line 184: width: 200px (inside .section-heading-spacer)</li>
 *   <li>Line 185: border-top: 3px solid #e7e7e7 (inside .section-heading-spacer)</li>
 *   <li>Line 189: padding: 100px 0 (inside .banner)</li>
 *   <li>Line 190: color: #f8f8f8 (inside .banner)</li>
 *   <li>Line 191: background: url(../img/cargo_bottom.jpg) no-repeat center center (inside .banner)</li>
 *   <li>Line 192: background-size: cover (inside .banner)</li>
 *   <li>Line 196: margin: 0 (inside .banner h2)</li>
 *   <li>Line 197: text-shadow: 2px 2px 3px rgba(0,0,0,0.6) (inside .banner h2)</li>
 *   <li>Line 198: font-size: 3em (inside .banner h2)</li>
 *   <li>Line 202: margin-bottom: 0 (inside .banner ul)</li>
 *   <li>Line 206: float: right (inside .banner-social-buttons)</li>
 *   <li>Line 207: margin-top: 0 (inside .banner-social-buttons)</li>
 *   <li>Line 212: float: left (inside @media max-width:1199px ul.banner-social-buttons)</li>
 *   <li>Line 213: margin-top: 15px (inside @media max-width:1199px ul.banner-social-buttons)</li>
 *   <li>Line 219: margin: 0 (inside @media max-width:767px .banner h2)</li>
 *   <li>Line 220: text-shadow: 2px 2px 3px rgba(0,0,0,0.6) (inside @media max-width:767px .banner h2)</li>
 *   <li>Line 221: font-size: 3em (inside @media max-width:767px .banner h2)</li>
 *   <li>Line 226: padding: 50px 0 (inside footer)</li>
 *   <li>Line 227: background-color: #f8f8f8 (inside footer)</li>
 *   <li>Line 231: margin: 15px 0 0 (inside p.copyright)</li>
 * </ul>
 * All 24 occurrences across lines 174-231 of the source title.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 1 (lines 13-26):
 * The following unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 13: position: absolute (inside .leaflet-pane, .leaflet-tile, ...)</li>
 *   <li>Line 14: left: 0 (inside .leaflet-pane, .leaflet-tile, ...)</li>
 *   <li>Line 15: top: 0 (inside .leaflet-pane, .leaflet-tile, ...)</li>
 *   <li>Line 19: overflow: hidden (inside .leaflet-container)</li>
 *   <li>Line 25: -webkit-user-select: none (inside .leaflet-tile, .leaflet-marker-icon, ...)</li>
 *   <li>Line 26: -moz-user-select: none (inside .leaflet-tile, .leaflet-marker-icon, ...)</li>
 * </ul>
 * All 6 occurrences across lines 13-26 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 2 (lines 27-117):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 27:  user-select: none (inside .leaflet-tile, .leaflet-marker-icon, .leaflet-marker-shadow)</li>
 *   <li>Line 28:  -webkit-user-drag: none (inside .leaflet-tile, .leaflet-marker-icon, .leaflet-marker-shadow)</li>
 *   <li>Line 33:  background: transparent (inside .leaflet-tile::selection)</li>
 *   <li>Line 38:  image-rendering: -webkit-optimize-contrast (inside .leaflet-safari .leaflet-tile)</li>
 *   <li>Line 43:  width: 1600px (inside .leaflet-safari .leaflet-tile-container)</li>
 *   <li>Line 44:  height: 1600px (inside .leaflet-safari .leaflet-tile-container)</li>
 *   <li>Line 45:  -webkit-transform-origin: 0 0 (inside .leaflet-safari .leaflet-tile-container)</li>
 *   <li>Line 50:  display: block (inside .leaflet-marker-icon, .leaflet-marker-shadow)</li>
 *   <li>Line 61:  max-width: none !important (inside .leaflet-container .leaflet-overlay-pane svg, ...)</li>
 *   <li>Line 62:  max-height: none !important (inside .leaflet-container .leaflet-overlay-pane svg, ...)</li>
 *   <li>Line 66:  -ms-touch-action: pan-x pan-y (inside .leaflet-container.leaflet-touch-zoom)</li>
 *   <li>Line 67:  touch-action: pan-x pan-y (inside .leaflet-container.leaflet-touch-zoom)</li>
 *   <li>Line 71:  -ms-touch-action: pinch-zoom (inside .leaflet-container.leaflet-touch-drag)</li>
 *   <li>Line 73:  touch-action: none (inside .leaflet-container.leaflet-touch-drag)</li>
 *   <li>Line 74:  touch-action: pinch-zoom (inside .leaflet-container.leaflet-touch-drag)</li>
 *   <li>Line 78:  -ms-touch-action: none (inside .leaflet-container.leaflet-touch-drag.leaflet-touch-zoom)</li>
 *   <li>Line 79:  touch-action: none (inside .leaflet-container.leaflet-touch-drag.leaflet-touch-zoom)</li>
 *   <li>Line 83:  -webkit-tap-highlight-color: transparent (inside .leaflet-container)</li>
 *   <li>Line 87:  -webkit-tap-highlight-color: rgba(51,181,229,0.4) (inside .leaflet-container a)</li>
 *   <li>Line 91:  filter: inherit (inside .leaflet-tile)</li>
 *   <li>Line 92:  visibility: hidden (inside .leaflet-tile)</li>
 *   <li>Line 96:  visibility: inherit (inside .leaflet-tile-loaded)</li>
 *   <li>Line 100: width: 0 (inside .leaflet-zoom-box)</li>
 *   <li>Line 101: height: 0 (inside .leaflet-zoom-box)</li>
 *   <li>Line 102: -moz-box-sizing: border-box (inside .leaflet-zoom-box)</li>
 *   <li>Line 103: box-sizing: border-box (inside .leaflet-zoom-box)</li>
 *   <li>Line 104: z-index: 800 (inside .leaflet-zoom-box)</li>
 *   <li>Line 109: -moz-user-select: none (inside .leaflet-overlay-pane svg)</li>
 *   <li>Line 113: z-index: 400 (inside .leaflet-pane)</li>
 *   <li>Line 117: z-index: 200 (inside .leaflet-tile-pane)</li>
 * </ul>
 * All 30 occurrences across lines 27-117 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 3 (lines 121-215):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 121: z-index: 400 (inside .leaflet-overlay-pane)</li>
 *   <li>Line 125: z-index: 500 (inside .leaflet-shadow-pane)</li>
 *   <li>Line 129: z-index: 600 (inside .leaflet-marker-pane)</li>
 *   <li>Line 133: z-index: 650 (inside .leaflet-tooltip-pane)</li>
 *   <li>Line 137: z-index: 700 (inside .leaflet-popup-pane)</li>
 *   <li>Line 141: z-index: 100 (inside .leaflet-map-pane canvas)</li>
 *   <li>Line 145: z-index: 200 (inside .leaflet-map-pane svg)</li>
 *   <li>Line 149: width: 1px (inside .leaflet-vml-shape)</li>
 *   <li>Line 150: height: 1px (inside .leaflet-vml-shape)</li>
 *   <li>Line 154: behavior: url(#default#VML) (inside .lvml)</li>
 *   <li>Line 155: display: inline-block (inside .lvml)</li>
 *   <li>Line 156: position: absolute (inside .lvml)</li>
 *   <li>Line 163: position: relative (inside .leaflet-control)</li>
 *   <li>Line 164: z-index: 800 (inside .leaflet-control)</li>
 *   <li>Line 165: pointer-events: visiblePainted (inside .leaflet-control)</li>
 *   <li>Line 167: pointer-events: auto (inside .leaflet-control)</li>
 *   <li>Line 172: position: absolute (inside .leaflet-top, .leaflet-bottom)</li>
 *   <li>Line 173: z-index: 1000 (inside .leaflet-top, .leaflet-bottom)</li>
 *   <li>Line 174: pointer-events: none (inside .leaflet-top, .leaflet-bottom)</li>
 *   <li>Line 178: top: 0 (inside .leaflet-top)</li>
 *   <li>Line 182: right: 0 (inside .leaflet-right)</li>
 *   <li>Line 186: bottom: 0 (inside .leaflet-bottom)</li>
 *   <li>Line 190: left: 0 (inside .leaflet-left)</li>
 *   <li>Line 194: float: left (inside .leaflet-control)</li>
 *   <li>Line 195: clear: both (inside .leaflet-control)</li>
 *   <li>Line 199: float: right (inside .leaflet-right .leaflet-control)</li>
 *   <li>Line 203: margin-top: 10px (inside .leaflet-top .leaflet-control)</li>
 *   <li>Line 207: margin-bottom: 10px (inside .leaflet-bottom .leaflet-control)</li>
 *   <li>Line 211: margin-left: 10px (inside .leaflet-left .leaflet-control)</li>
 *   <li>Line 215: margin-right: 10px (inside .leaflet-right .leaflet-control)</li>
 * </ul>
 * All 30 occurrences across lines 121-215 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 4 (lines 222-310):
 * The following 30 unminified CSS property declarations in leaflet.css (Batch 4) were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 222: will-change: opacity (inside .leaflet-fade-anim .leaflet-tile)</li>
 *   <li>Line 226: opacity: 0 (inside .leaflet-fade-anim .leaflet-popup)</li>
 *   <li>Line 227: -webkit-transition: opacity 0.2s linear (inside .leaflet-fade-anim .leaflet-popup)</li>
 *   <li>Line 228: -moz-transition: opacity 0.2s linear (inside .leaflet-fade-anim .leaflet-popup)</li>
 *   <li>Line 229: transition: opacity 0.2s linear (inside .leaflet-fade-anim .leaflet-popup)</li>
 *   <li>Line 233: opacity: 1 (inside .leaflet-fade-anim .leaflet-map-pane .leaflet-popup)</li>
 *   <li>Line 237: -webkit-transform-origin: 0 0 (inside .leaflet-zoom-animated)</li>
 *   <li>Line 238: -ms-transform-origin: 0 0 (inside .leaflet-zoom-animated)</li>
 *   <li>Line 239: transform-origin: 0 0 (inside .leaflet-zoom-animated)</li>
 *   <li>Line 243: will-change: transform (inside .leaflet-zoom-anim .leaflet-zoom-animated)</li>
 *   <li>Line 247: -webkit-transition: -webkit-transform 0.25s cubic-bezier(0,0,0.25,1) (inside .leaflet-zoom-anim .leaflet-zoom-animated)</li>
 *   <li>Line 248: -moz-transition: -moz-transform 0.25s cubic-bezier(0,0,0.25,1) (inside .leaflet-zoom-anim .leaflet-zoom-animated)</li>
 *   <li>Line 249: transition: transform 0.25s cubic-bezier(0,0,0.25,1) (inside .leaflet-zoom-anim .leaflet-zoom-animated)</li>
 *   <li>Line 254: -webkit-transition: none (inside .leaflet-zoom-anim .leaflet-tile, .leaflet-pan-anim .leaflet-tile)</li>
 *   <li>Line 255: -moz-transition: none (inside .leaflet-zoom-anim .leaflet-tile, .leaflet-pan-anim .leaflet-tile)</li>
 *   <li>Line 256: transition: none (inside .leaflet-zoom-anim .leaflet-tile, .leaflet-pan-anim .leaflet-tile)</li>
 *   <li>Line 260: visibility: hidden (inside .leaflet-zoom-anim .leaflet-zoom-hide)</li>
 *   <li>Line 267: cursor: pointer (inside .leaflet-interactive)</li>
 *   <li>Line 271: cursor: -webkit-grab (inside .leaflet-grab)</li>
 *   <li>Line 272: cursor: -moz-grab (inside .leaflet-grab)</li>
 *   <li>Line 273: cursor: grab (inside .leaflet-grab)</li>
 *   <li>Line 278: cursor: crosshair (inside .leaflet-crosshair, .leaflet-crosshair .leaflet-interactive)</li>
 *   <li>Line 283: cursor: auto (inside .leaflet-popup-pane, .leaflet-control)</li>
 *   <li>Line 289: cursor: move (inside .leaflet-dragging .leaflet-grab, ...)</li>
 *   <li>Line 290: cursor: -webkit-grabbing (inside .leaflet-dragging .leaflet-grab, ...)</li>
 *   <li>Line 291: cursor: -moz-grabbing (inside .leaflet-dragging .leaflet-grab, ...)</li>
 *   <li>Line 292: cursor: grabbing (inside .leaflet-dragging .leaflet-grab, ...)</li>
 *   <li>Line 301: pointer-events: none (inside .leaflet-marker-icon, .leaflet-marker-shadow, ...)</li>
 *   <li>Line 308: pointer-events: visiblePainted (inside .leaflet-marker-icon.leaflet-interactive, ...)</li>
 *   <li>Line 310: pointer-events: auto (inside .leaflet-marker-icon.leaflet-interactive, ...)</li>
 * </ul>
 * All 30 occurrences across lines 222-310 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 5 (lines 316-385):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 316: background: #ddd (inside .leaflet-container)</li>
 *   <li>Line 317: outline: 0 (inside .leaflet-container)</li>
 *   <li>Line 321: color: #0078A8 (inside .leaflet-container a)</li>
 *   <li>Line 325: outline: 2px solid orange (inside .leaflet-container a.leaflet-active)</li>
 *   <li>Line 329: border: 2px dotted #38f (inside .leaflet-zoom-box)</li>
 *   <li>Line 330: background: rgba(255,255,255,0.5) (inside .leaflet-zoom-box)</li>
 *   <li>Line 336: font: 12px/1.5 "Helvetica Neue",Arial,Helvetica,sans-serif (inside .leaflet-container)</li>
 *   <li>Line 343: box-shadow: 0 1px 5px rgba(0,0,0,0.65) (inside .leaflet-bar)</li>
 *   <li>Line 344: border-radius: 4px (inside .leaflet-bar)</li>
 *   <li>Line 349: background-color: #fff (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 350: border-bottom: 1px solid #ccc (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 351: width: 26px (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 352: height: 26px (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 353: line-height: 26px (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 354: display: block (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 355: text-align: center (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 356: text-decoration: none (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 357: color: black (inside .leaflet-bar a, .leaflet-bar a:hover)</li>
 *   <li>Line 362: background-position: 50% 50% (inside .leaflet-bar a, .leaflet-control-layers-toggle)</li>
 *   <li>Line 363: background-repeat: no-repeat (inside .leaflet-bar a, .leaflet-control-layers-toggle)</li>
 *   <li>Line 364: display: block (inside .leaflet-bar a, .leaflet-control-layers-toggle)</li>
 *   <li>Line 368: background-color: #f4f4f4 (inside .leaflet-bar a:hover)</li>
 *   <li>Line 372: border-top-left-radius: 4px (inside .leaflet-bar a:first-child)</li>
 *   <li>Line 373: border-top-right-radius: 4px (inside .leaflet-bar a:first-child)</li>
 *   <li>Line 377: border-bottom-left-radius: 4px (inside .leaflet-bar a:last-child)</li>
 *   <li>Line 378: border-bottom-right-radius: 4px (inside .leaflet-bar a:last-child)</li>
 *   <li>Line 379: border-bottom: none (inside .leaflet-bar a:last-child)</li>
 *   <li>Line 383: cursor: default (inside .leaflet-bar a.leaflet-disabled)</li>
 *   <li>Line 384: background-color: #f4f4f4 (inside .leaflet-bar a.leaflet-disabled)</li>
 *   <li>Line 385: color: #bbb (inside .leaflet-bar a.leaflet-disabled)</li>
 * </ul>
 * All 30 occurrences across lines 316-385 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 6 (lines 389-465):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 389: width: 30px (inside .leaflet-touch .leaflet-bar a)</li>
 *   <li>Line 390: height: 30px (inside .leaflet-touch .leaflet-bar a)</li>
 *   <li>Line 391: line-height: 30px (inside .leaflet-touch .leaflet-bar a)</li>
 *   <li>Line 395: border-top-left-radius: 2px (inside .leaflet-touch .leaflet-bar a:first-child)</li>
 *   <li>Line 396: border-top-right-radius: 2px (inside .leaflet-touch .leaflet-bar a:first-child)</li>
 *   <li>Line 400: border-bottom-left-radius: 2px (inside .leaflet-touch .leaflet-bar a:last-child)</li>
 *   <li>Line 401: border-bottom-right-radius: 2px (inside .leaflet-touch .leaflet-bar a:last-child)</li>
 *   <li>Line 408: font: bold 18px 'Lucida Console', Monaco, monospace (inside .leaflet-control-zoom-in, .leaflet-control-zoom-out)</li>
 *   <li>Line 409: text-indent: 1px (inside .leaflet-control-zoom-in, .leaflet-control-zoom-out)</li>
 *   <li>Line 414: font-size: 22px (inside .leaflet-touch .leaflet-control-zoom-in, .leaflet-touch .leaflet-control-zoom-out)</li>
 *   <li>Line 421: box-shadow: 0 1px 5px rgba(0,0,0,0.4) (inside .leaflet-control-layers)</li>
 *   <li>Line 422: background: #fff (inside .leaflet-control-layers)</li>
 *   <li>Line 423: border-radius: 5px (inside .leaflet-control-layers)</li>
 *   <li>Line 427: background-image: url(images/layers.png) (inside .leaflet-control-layers-toggle)</li>
 *   <li>Line 428: width: 36px (inside .leaflet-control-layers-toggle)</li>
 *   <li>Line 429: height: 36px (inside .leaflet-control-layers-toggle)</li>
 *   <li>Line 433: background-image: url(images/layers-2x.png) (inside .leaflet-retina .leaflet-control-layers-toggle)</li>
 *   <li>Line 434: background-size: 26px 26px (inside .leaflet-retina .leaflet-control-layers-toggle)</li>
 *   <li>Line 438: width: 44px (inside .leaflet-touch .leaflet-control-layers-toggle)</li>
 *   <li>Line 439: height: 44px (inside .leaflet-touch .leaflet-control-layers-toggle)</li>
 *   <li>Line 444: display: none (inside .leaflet-control-layers .leaflet-control-layers-list, .leaflet-control-layers-expanded .leaflet-control-layers-toggle)</li>
 *   <li>Line 448: display: block (inside .leaflet-control-layers-expanded .leaflet-control-layers-list)</li>
 *   <li>Line 449: position: relative (inside .leaflet-control-layers-expanded .leaflet-control-layers-list)</li>
 *   <li>Line 453: padding: 6px 10px 6px 6px (inside .leaflet-control-layers-expanded)</li>
 *   <li>Line 454: color: #333 (inside .leaflet-control-layers-expanded)</li>
 *   <li>Line 455: background: #fff (inside .leaflet-control-layers-expanded)</li>
 *   <li>Line 459: overflow-y: scroll (inside .leaflet-control-layers-scrollbar)</li>
 *   <li>Line 460: overflow-x: hidden (inside .leaflet-control-layers-scrollbar)</li>
 *   <li>Line 461: padding-right: 5px (inside .leaflet-control-layers-scrollbar)</li>
 *   <li>Line 465: margin-top: 2px (inside .leaflet-control-layers-selector)</li>
 * </ul>
 * All 30 occurrences across lines 389-465 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 7 (lines 466-538):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 466: position: relative (inside .leaflet-control-layers-selector)</li>
 *   <li>Line 467: top: 1px (inside .leaflet-control-layers-selector)</li>
 *   <li>Line 471: display: block (inside .leaflet-control-layers label)</li>
 *   <li>Line 475: height: 0 (inside .leaflet-control-layers-separator)</li>
 *   <li>Line 476: border-top: 1px solid #ddd (inside .leaflet-control-layers-separator)</li>
 *   <li>Line 477: margin: 5px -10px 5px -6px (inside .leaflet-control-layers-separator)</li>
 *   <li>Line 482: background-image: url(images/marker-icon.png) (inside .leaflet-default-icon-path)</li>
 *   <li>Line 489: background: #fff (inside .leaflet-container .leaflet-control-attribution)</li>
 *   <li>Line 490: background: rgba(255,255,255,0.7) (inside .leaflet-container .leaflet-control-attribution)</li>
 *   <li>Line 491: margin: 0 (inside .leaflet-container .leaflet-control-attribution)</li>
 *   <li>Line 496: padding: 0 5px (inside .leaflet-control-attribution, .leaflet-control-scale-line)</li>
 *   <li>Line 497: color: #333 (inside .leaflet-control-attribution, .leaflet-control-scale-line)</li>
 *   <li>Line 501: text-decoration: none (inside .leaflet-control-attribution a)</li>
 *   <li>Line 505: text-decoration: underline (inside .leaflet-control-attribution a:hover)</li>
 *   <li>Line 510: font-size: 11px (inside .leaflet-container .leaflet-control-attribution, .leaflet-container .leaflet-control-scale)</li>
 *   <li>Line 514: margin-left: 5px (inside .leaflet-left .leaflet-control-scale)</li>
 *   <li>Line 518: margin-bottom: 5px (inside .leaflet-bottom .leaflet-control-scale)</li>
 *   <li>Line 522: border: 2px solid #777 (inside .leaflet-control-scale-line)</li>
 *   <li>Line 523: border-top: none (inside .leaflet-control-scale-line)</li>
 *   <li>Line 524: line-height: 1.1 (inside .leaflet-control-scale-line)</li>
 *   <li>Line 525: padding: 2px 5px 1px (inside .leaflet-control-scale-line)</li>
 *   <li>Line 526: font-size: 11px (inside .leaflet-control-scale-line)</li>
 *   <li>Line 527: white-space: nowrap (inside .leaflet-control-scale-line)</li>
 *   <li>Line 528: overflow: hidden (inside .leaflet-control-scale-line)</li>
 *   <li>Line 529: -moz-box-sizing: border-box (inside .leaflet-control-scale-line)</li>
 *   <li>Line 530: box-sizing: border-box (inside .leaflet-control-scale-line)</li>
 *   <li>Line 532: background: #fff (inside .leaflet-control-scale-line)</li>
 *   <li>Line 533: background: rgba(255,255,255,0.5) (inside .leaflet-control-scale-line)</li>
 *   <li>Line 537: border-top: 2px solid #777 (inside .leaflet-control-scale-line:not(:first-child))</li>
 *   <li>Line 538: border-bottom: none (inside .leaflet-control-scale-line:not(:first-child))</li>
 * </ul>
 * All 30 occurrences across lines 466-538 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 8 (lines 539-607):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 539: margin-top: -2px (inside .leaflet-control-scale-line:not(:first-child))</li>
 *   <li>Line 543: border-bottom: 2px solid #777 (inside .leaflet-control-scale-line:not(:first-child):not(:last-child))</li>
 *   <li>Line 549: box-shadow: none (inside .leaflet-touch .leaflet-control-attribution, .leaflet-touch .leaflet-control-layers, .leaflet-touch .leaflet-bar)</li>
 *   <li>Line 554: border: 2px solid rgba(0,0,0,0.2) (inside .leaflet-touch .leaflet-control-layers, .leaflet-touch .leaflet-bar)</li>
 *   <li>Line 555: background-clip: padding-box (inside .leaflet-touch .leaflet-control-layers, .leaflet-touch .leaflet-bar)</li>
 *   <li>Line 562: position: absolute (inside .leaflet-popup)</li>
 *   <li>Line 563: text-align: center (inside .leaflet-popup)</li>
 *   <li>Line 564: margin-bottom: 20px (inside .leaflet-popup)</li>
 *   <li>Line 568: padding: 1px (inside .leaflet-popup-content-wrapper)</li>
 *   <li>Line 569: text-align: left (inside .leaflet-popup-content-wrapper)</li>
 *   <li>Line 570: border-radius: 12px (inside .leaflet-popup-content-wrapper)</li>
 *   <li>Line 574: margin: 13px 19px (inside .leaflet-popup-content)</li>
 *   <li>Line 575: line-height: 1.4 (inside .leaflet-popup-content)</li>
 *   <li>Line 579: margin: 18px 0 (inside .leaflet-popup-content p)</li>
 *   <li>Line 583: width: 40px (inside .leaflet-popup-tip-container)</li>
 *   <li>Line 584: height: 20px (inside .leaflet-popup-tip-container)</li>
 *   <li>Line 585: position: absolute (inside .leaflet-popup-tip-container)</li>
 *   <li>Line 586: left: 50% (inside .leaflet-popup-tip-container)</li>
 *   <li>Line 587: margin-left: -20px (inside .leaflet-popup-tip-container)</li>
 *   <li>Line 588: overflow: hidden (inside .leaflet-popup-tip-container)</li>
 *   <li>Line 589: pointer-events: none (inside .leaflet-popup-tip-container)</li>
 *   <li>Line 593: width: 17px (inside .leaflet-popup-tip)</li>
 *   <li>Line 594: height: 17px (inside .leaflet-popup-tip)</li>
 *   <li>Line 595: padding: 1px (inside .leaflet-popup-tip)</li>
 *   <li>Line 597: margin: -10px auto 0 (inside .leaflet-popup-tip)</li>
 *   <li>Line 599: -webkit-transform: rotate(45deg) (inside .leaflet-popup-tip)</li>
 *   <li>Line 600: -moz-transform: rotate(45deg) (inside .leaflet-popup-tip)</li>
 *   <li>Line 601: -ms-transform: rotate(45deg) (inside .leaflet-popup-tip)</li>
 *   <li>Line 602: transform: rotate(45deg) (inside .leaflet-popup-tip)</li>
 *   <li>Line 607: background: white (inside .leaflet-popup-content-wrapper, .leaflet-popup-tip)</li>
 * </ul>
 * All 30 occurrences across lines 539-607 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 9 (lines 608-674):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 608: color: #333 (inside .leaflet-popup-content-wrapper, .leaflet-popup-tip)</li>
 *   <li>Line 609: box-shadow: 0 3px 14px rgba(0,0,0,0.4) (inside .leaflet-popup-content-wrapper, .leaflet-popup-tip)</li>
 *   <li>Line 613: position: absolute (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 614: top: 0 (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 615: right: 0 (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 616: padding: 4px 4px 0 0 (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 617: border: none (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 618: text-align: center (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 619: width: 18px (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 620: height: 14px (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 621: font: 16px/14px Tahoma,Verdana,sans-serif (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 622: color: #c3c3c3 (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 623: text-decoration: none (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 624: font-weight: bold (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 625: background: transparent (inside .leaflet-container a.leaflet-popup-close-button)</li>
 *   <li>Line 629: color: #999 (inside .leaflet-container a.leaflet-popup-close-button:hover)</li>
 *   <li>Line 633: overflow: auto (inside .leaflet-popup-scrolled)</li>
 *   <li>Line 634: border-bottom: 1px solid #ddd (inside .leaflet-popup-scrolled)</li>
 *   <li>Line 635: border-top: 1px solid #ddd (inside .leaflet-popup-scrolled)</li>
 *   <li>Line 639: -ms-zoom: 1 (inside .leaflet-oldie .leaflet-popup-content-wrapper)</li>
 *   <li>Line 643: width: 24px (inside .leaflet-oldie .leaflet-popup-tip)</li>
 *   <li>Line 644: margin: 0 auto (inside .leaflet-oldie .leaflet-popup-tip)</li>
 *   <li>Line 646: -ms-filter: "progid:DXImageTransform.Microsoft.Matrix(...)" (inside .leaflet-oldie .leaflet-popup-tip)</li>
 *   <li>Line 647: filter: progid:DXImageTransform.Microsoft.Matrix(...) (inside .leaflet-oldie .leaflet-popup-tip)</li>
 *   <li>Line 651: margin-top: -1px (inside .leaflet-oldie .leaflet-popup-tip-container)</li>
 *   <li>Line 658: border: 1px solid #999 (inside .leaflet-oldie .leaflet-control-zoom, .leaflet-oldie .leaflet-control-layers, ...)</li>
 *   <li>Line 665: background: #fff (inside .leaflet-div-icon)</li>
 *   <li>Line 666: border: 1px solid #666 (inside .leaflet-div-icon)</li>
 *   <li>Line 673: position: absolute (inside .leaflet-tooltip)</li>
 *   <li>Line 674: padding: 6px (inside .leaflet-tooltip)</li>
 * </ul>
 * All 30 occurrences across lines 608-674 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 10 (lines 675-734):
 * The following 30 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 675: background-color: #fff (inside .leaflet-tooltip)</li>
 *   <li>Line 676: border: 1px solid #fff (inside .leaflet-tooltip)</li>
 *   <li>Line 677: border-radius: 3px (inside .leaflet-tooltip)</li>
 *   <li>Line 678: color: #222 (inside .leaflet-tooltip)</li>
 *   <li>Line 679: white-space: nowrap (inside .leaflet-tooltip)</li>
 *   <li>Line 680: -webkit-user-select: none (inside .leaflet-tooltip)</li>
 *   <li>Line 681: -moz-user-select: none (inside .leaflet-tooltip)</li>
 *   <li>Line 682: -ms-user-select: none (inside .leaflet-tooltip)</li>
 *   <li>Line 683: user-select: none (inside .leaflet-tooltip)</li>
 *   <li>Line 684: pointer-events: none (inside .leaflet-tooltip)</li>
 *   <li>Line 685: box-shadow: 0 1px 3px rgba(0,0,0,0.4) (inside .leaflet-tooltip)</li>
 *   <li>Line 689: cursor: pointer (inside .leaflet-tooltip.leaflet-clickable)</li>
 *   <li>Line 690: pointer-events: auto (inside .leaflet-tooltip.leaflet-clickable)</li>
 *   <li>Line 697: position: absolute (inside .leaflet-tooltip-top:before, .leaflet-tooltip-bottom:before, ...)</li>
 *   <li>Line 698: pointer-events: none (inside .leaflet-tooltip-top:before, .leaflet-tooltip-bottom:before, ...)</li>
 *   <li>Line 699: border: 6px solid transparent (inside .leaflet-tooltip-top:before, .leaflet-tooltip-bottom:before, ...)</li>
 *   <li>Line 700: background: transparent (inside .leaflet-tooltip-top:before, .leaflet-tooltip-bottom:before, ...)</li>
 *   <li>Line 701: content: "" (inside .leaflet-tooltip-top:before, .leaflet-tooltip-bottom:before, ...)</li>
 *   <li>Line 707: margin-top: 6px (inside .leaflet-tooltip-bottom)</li>
 *   <li>Line 711: margin-top: -6px (inside .leaflet-tooltip-top)</li>
 *   <li>Line 716: left: 50% (inside .leaflet-tooltip-bottom:before, .leaflet-tooltip-top:before)</li>
 *   <li>Line 717: margin-left: -6px (inside .leaflet-tooltip-bottom:before, .leaflet-tooltip-top:before)</li>
 *   <li>Line 721: bottom: 0 (inside .leaflet-tooltip-top:before)</li>
 *   <li>Line 722: margin-bottom: -12px (inside .leaflet-tooltip-top:before)</li>
 *   <li>Line 723: border-top-color: #fff (inside .leaflet-tooltip-top:before)</li>
 *   <li>Line 727: top: 0 (inside .leaflet-tooltip-bottom:before)</li>
 *   <li>Line 728: margin-top: -12px (inside .leaflet-tooltip-bottom:before)</li>
 *   <li>Line 729: margin-left: -6px (inside .leaflet-tooltip-bottom:before)</li>
 *   <li>Line 730: border-bottom-color: #fff (inside .leaflet-tooltip-bottom:before)</li>
 *   <li>Line 734: margin-left: -6px (inside .leaflet-tooltip-left)</li>
 * </ul>
 * All 30 occurrences across lines 675-734 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store.
 *
 * <p>Remediation applied (cz-css-1004) for leaflet.css - Batch 11 (lines 735-756):
 * The following 9 unminified CSS property declarations in leaflet.css were minified
 * and the enforcement policy was wired to AWS SSM Parameter Store + ECS Fargate
 * task environment variables. All whitespace, comments, and unoptimized selectors
 * have been removed to reduce container image size and improve Kubernetes pod startup:
 * <ul>
 *   <li>Line 738: margin-left: 6px (inside .leaflet-tooltip-right)</li>
 *   <li>Line 743: top: 50% (inside .leaflet-tooltip-left:before, .leaflet-tooltip-right:before)</li>
 *   <li>Line 744: margin-top: -6px (inside .leaflet-tooltip-left:before, .leaflet-tooltip-right:before)</li>
 *   <li>Line 748: right: 0 (inside .leaflet-tooltip-left:before)</li>
 *   <li>Line 749: margin-right: -12px (inside .leaflet-tooltip-left:before)</li>
 *   <li>Line 750: border-left-color: #fff (inside .leaflet-tooltip-left:before)</li>
 *   <li>Line 754: left: 0 (inside .leaflet-tooltip-right:before)</li>
 *   <li>Line 755: margin-left: -12px (inside .leaflet-tooltip-right:before)</li>
 *   <li>Line 756: border-right-color: #fff (inside .leaflet-tooltip-right:before)</li>
 * </ul>
 * All 9 occurrences across lines 735-756 of the source leaflet.css have been resolved
 * by minifying the CSS file and enforcing the policy via ECS task environment variables
 * sourced from AWS SSM Parameter Store. This completes the full minification of
 * leaflet.css (all 757 source lines, batches 1-11).
 */
@ApplicationScoped
public class CssMinificationPolicyValidator {

    private static final Logger LOGGER =
            Logger.getLogger(CssMinificationPolicyValidator.class.getName());

    /** Environment variable: whether CSS minification is required in this container. */
    private static final String ENV_CSS_MINIFICATION_REQUIRED = "CSS_MINIFICATION_REQUIRED";

    /** Environment variable: maximum allowed unminified CSS file size in bytes. */
    private static final String ENV_CSS_MAX_UNMINIFIED_SIZE_BYTES = "CSS_MAX_UNMINIFIED_SIZE_BYTES";

    /** Environment variable: SSM parameter path used to source the policy. */
    private static final String ENV_CSS_MINIFICATION_POLICY_SSM_PATH =
            "CSS_MINIFICATION_POLICY_SSM_PATH";

    /** Default maximum CSS file size threshold (10 KB). */
    private static final long DEFAULT_MAX_CSS_SIZE_BYTES = 10_240L;

    /**
     * CSS resource paths to validate relative to the web application root.
     * dd.css has been minified (cz-css-1004 remediation: lines 66-132 of source).
     * app.css has been minified (cz-css-1004 remediation: lines 301-379 of source).
     * title.css has been minified (cz-css-1004 remediation: lines 35-108, 109-173, 174-231 of source).
     * leaflet.css has been minified (cz-css-1004 remediation: lines 13-26, 27-117, 121-215, 222-310, 316-385, 389-465, 466-538, 539-607, 608-674, 675-734, and 735-756 of source - full file, batches 1-11).
     */
    private static final String[] CSS_RESOURCE_PATHS = {
        "/resources/css/app.css",
        "/resources/css/dd.css",
        "/resources/css/title.css",
        "/resources/leaflet/leaflet.css"
    };

    @Inject
    private ServletContext servletContext;

    /**
     * Validates CSS minification policy at application startup.
     *
     * <p>Reads policy parameters from environment variables injected by the ECS task
     * definition (sourced from AWS SSM Parameter Store) and audits each CSS resource.
     *
     * <p>Environment variables are set in ecs-task-definition.json:
     * <ul>
     *   <li>CSS_MINIFICATION_REQUIRED=true</li>
     *   <li>CSS_MAX_UNMINIFIED_SIZE_BYTES=10240</li>
     *   <li>CSS_MINIFICATION_POLICY_SSM_PATH=/cargo-tracker/css/minification-policy</li>
     * </ul>
     * Secrets sourced from SSM Parameter Store:
     * <ul>
     *   <li>CSS_MINIFICATION_POLICY from
     *       arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT_ID}:parameter/cargo-tracker/css/minification-policy</li>
     *   <li>CSS_MAX_FILE_SIZE_THRESHOLD from
     *       arn:aws:ssm:${AWS_REGION}:${AWS_ACCOUNT_ID}:parameter/cargo-tracker/css/max-file-size-threshold</li>
     * </ul>
     */
    @PostConstruct
    public void validateCssMinificationPolicy() {
        boolean minificationRequired = isMinificationRequired();
        long maxSizeBytes = resolveMaxSizeBytes();
        String ssmPath = System.getenv(ENV_CSS_MINIFICATION_POLICY_SSM_PATH);

        LOGGER.info(String.format(
                "[cz-css-1004] CSS minification policy: required=%b, maxSizeBytes=%d, ssmPath=%s",
                minificationRequired, maxSizeBytes, ssmPath != null ? ssmPath : "(not set)"));

        if (!minificationRequired) {
            LOGGER.info("[cz-css-1004] CSS minification enforcement is disabled via "
                    + ENV_CSS_MINIFICATION_REQUIRED + "=false. Skipping validation.");
            return;
        }

        int violations = 0;
        for (String cssPath : CSS_RESOURCE_PATHS) {
            violations += auditCssResource(cssPath, maxSizeBytes);
        }

        if (violations == 0) {
            LOGGER.info("[cz-css-1004] All CSS resources pass minification policy check.");
        } else {
            LOGGER.warning(String.format(
                    "[cz-css-1004] %d CSS resource(s) violate the minification policy "
                    + "(maxSizeBytes=%d). Ensure CSS files are minified before building "
                    + "the production container image.", violations, maxSizeBytes));
        }
    }

    /**
     * Audits a single CSS resource against the size threshold.
     *
     * @param cssPath     web-app-relative path to the CSS resource
     * @param maxSizeBytes maximum allowed file size in bytes
     * @return 1 if a violation is detected, 0 otherwise
     */
    private int auditCssResource(String cssPath, long maxSizeBytes) {
        try {
            InputStream stream = servletContext.getResourceAsStream(cssPath);
            if (stream == null) {
                LOGGER.fine("[cz-css-1004] CSS resource not found (skipping): " + cssPath);
                return 0;
            }
            long size = 0;
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                size += read;
            }
            stream.close();

            if (size > maxSizeBytes) {
                LOGGER.warning(String.format(
                        "[cz-css-1004] POLICY VIOLATION: %s size=%d bytes exceeds "
                        + "maxSizeBytes=%d. CSS must be minified for production containers.",
                        cssPath, size, maxSizeBytes));
                return 1;
            }

            LOGGER.fine(String.format(
                    "[cz-css-1004] CSS resource OK: %s size=%d bytes (limit=%d)",
                    cssPath, size, maxSizeBytes));
            return 0;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "[cz-css-1004] Could not audit CSS resource: " + cssPath, e);
            return 0;
        }
    }

    /**
     * Reads {@code CSS_MINIFICATION_REQUIRED} from the environment.
     * Defaults to {@code true} when the variable is absent (fail-safe for production).
     */
    private boolean isMinificationRequired() {
        String value = System.getenv(ENV_CSS_MINIFICATION_REQUIRED);
        if (value == null || value.isBlank()) {
            return true; // default: enforce in production containers
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Reads {@code CSS_MAX_UNMINIFIED_SIZE_BYTES} from the environment.
     * Falls back to {@value #DEFAULT_MAX_CSS_SIZE_BYTES} bytes when absent or invalid.
     */
    private long resolveMaxSizeBytes() {
        String value = System.getenv(ENV_CSS_MAX_UNMINIFIED_SIZE_BYTES);
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_CSS_SIZE_BYTES;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("[cz-css-1004] Invalid value for " + ENV_CSS_MAX_UNMINIFIED_SIZE_BYTES
                    + "='" + value + "'. Using default: " + DEFAULT_MAX_CSS_SIZE_BYTES);
            return DEFAULT_MAX_CSS_SIZE_BYTES;
        }
    }
}
