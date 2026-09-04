module.exports = {
  plugins: [
    require('cssnano')({
      preset: ['default', {
        discardComments: { removeAll: true },
        normalizeWhitespace: true,
        minifySelectors: true,
        minifyParams: true,
        reduceIdents: false
      }]
    })
  ]
};
