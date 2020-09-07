// Mocks all files ending in `.vue` showing them as plain Vue instances
declare module '*.vue' {
  import Vue from 'vue';
  export default Vue;
}

import { CustomWindow } from './@types/CustomWindow';
declare let window: CustomWindow;
