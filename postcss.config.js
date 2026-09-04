// PostCSS configuration for CSS minification in the production container build.
// Used by the css-minifier stage in the multi-stage Dockerfile to strip
// whitespace, comments, and redundant declarations from all CSS assets before
// they are packaged into the final image, reducing ECR storage and pod startup time.
module.exports = {
  plugins: [
    require('cssnano')({
      preset: [
        'default',
        {
          // Remove all comments (including licence comments) for maximum compression
          discardComments: { removeAll: true },
          // Normalise whitespace aggressively
          normalizeWhitespace: true,
          // Merge duplicate rules / selectors
          mergeLonghand: true,
          mergeRules: true,
          // Minify font values, gradients, selectors, etc.
          minifyFontValues: true,
          minifyGradients: true,
          minifySelectors: true,
          // Remove redundant values
          reduceIdents: false,
          // Keep z-index values as-is to avoid ordering issues
          zindex: false
        }
      ]
    })
  ]
};
