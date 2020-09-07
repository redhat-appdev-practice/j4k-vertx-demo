export interface Pod {
  id: string;
  lastUpdate?: Date;
}

export interface StateInterface {
  pods: Pod[];
  currentPod?: string;
}

const state: StateInterface = {
  pods: [
  ],
  currentPod: '096eef72-f05d-11ea-9b99-ef74f95edc4b'
};

export default state;
