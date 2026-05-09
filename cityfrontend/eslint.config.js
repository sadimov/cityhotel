// @ts-check
// Flat config for ESLint v9 + @angular-eslint v21 + @typescript-eslint v8.
// Migration Tour 2B (2026-05-06) : passage Angular 20 -> 21.2.x. Le legacy
// .eslintrc.json n'est plus supporte par @typescript-eslint v8 + ESLint v9.
// Ce fichier reproduit l'ancienne configuration avec la nouvelle API plate.
//
// Conventions projet :
//  - Architecture NgModule (pas standalone) -> regle prefer-standalone desactivee.
//  - Selecteurs prefixes "app", composants en kebab-case, directives en camelCase.

const js = require('@eslint/js');
const globals = require('globals');
const tsParser = require('@typescript-eslint/parser');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const angularEslint = require('@angular-eslint/eslint-plugin');
const angularTemplate = require('@angular-eslint/eslint-plugin-template');
const angularTemplateParser = require('@angular-eslint/template-parser');

const angularRecommendedTs = require('@angular-eslint/eslint-plugin/dist/configs/recommended.json');
const angularRecommendedTpl = require('@angular-eslint/eslint-plugin-template/dist/configs/recommended.json');
const angularA11yTpl = require('@angular-eslint/eslint-plugin-template/dist/configs/accessibility.json');

module.exports = [
  // Patterns ignores globalement.
  {
    ignores: [
      'projects/**/*',
      'dist/**/*',
      'node_modules/**/*',
      '.angular/**/*',
      'coverage/**/*',
    ],
  },

  // Regles TypeScript / composants Angular.
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
      },
      globals: {
        ...globals.browser,
        ...globals.es2022,
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      '@angular-eslint': angularEslint,
      '@angular-eslint/template': angularTemplate,
    },
    processor: '@angular-eslint/template/extract-inline-html',
    rules: {
      ...js.configs.recommended.rules,
      ...tsPlugin.configs.recommended.rules,
      ...angularRecommendedTs.rules,

      // Architecture NgModule assumee -> on ne pousse pas vers standalone.
      '@angular-eslint/prefer-standalone': 'off',

      // Conventions projet (selecteurs).
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],

      // Equivalent regles TS personnalisees historiques.
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': 'warn',
      '@typescript-eslint/explicit-function-return-type': 'off',
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/no-empty-function': 'warn',
      '@typescript-eslint/no-inferrable-types': 'off',
    },
  },

  // Specs Karma/Jasmine : ajouter les globals Jasmine.
  {
    files: ['**/*.spec.ts', 'src/test.ts'],
    languageOptions: {
      globals: {
        ...globals.jasmine,
      },
    },
  },

  // Templates HTML : recommended + accessibility (idem ancien .eslintrc.json).
  {
    files: ['**/*.html'],
    languageOptions: {
      parser: angularTemplateParser,
    },
    plugins: {
      '@angular-eslint/template': angularTemplate,
    },
    rules: {
      ...angularRecommendedTpl.rules,
      ...angularA11yTpl.rules,

      // Conserves en warning comme dans l'ancien fichier.
      '@angular-eslint/template/interactive-supports-focus': 'warn',
      '@angular-eslint/template/click-events-have-key-events': 'warn',
    },
  },
];
