export interface Pod {
  id: string;
  lastUpdate?: Date;
  requestCount?: number;
  appname?: string;
}

export interface StateInterface {
  pods: Pod[];
  currentPod?: string;
  sessionIncrement: number;
}

const state: StateInterface = {
  pods: [],
  sessionIncrement: 0
};

export default state;
