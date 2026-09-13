const { createRequire } = require('node:module');
const { join } = require('node:path');

// Keep formatting dependencies beside the existing frontend toolchain.
const frontendRequire = createRequire(join(__dirname, 'frontend/package.json'));

module.exports = {
  printWidth: 100,
  tabWidth: 2,
  useTabs: false,
  singleQuote: true,
  trailingComma: 'all',
  endOfLine: 'lf',
  embeddedLanguageFormatting: 'auto',
  htmlWhitespaceSensitivity: 'ignore',
  plugins: [
    frontendRequire.resolve('prettier-plugin-java'),
    frontendRequire.resolve('@prettier/plugin-xml'),
  ],
  overrides: [
    { files: '*.java', options: { tabWidth: 4 } },
    { files: '*.xml', options: { xmlWhitespaceSensitivity: 'ignore' } },
  ],
};
