import { Module } from 'vuex';
import state, { StateInterface } from './state';
import mutations from './mutations';

const exampleModule: Module<StateInterface, StateInterface> = {
  namespaced: true,
  mutations,
  state
};

export default exampleModule;
