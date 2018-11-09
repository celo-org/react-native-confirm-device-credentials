// @flow


type ConfirmDeviceCredentialsModule = {
  isDeviceSecure: () => Promise<boolean>,
  makeDeviceSecure: (message: string, actionButtonLabel: string) => Promise<boolean>,
  keystoreInit: (keyName: string, reauthenticationTimeoutInSecs: number, invalidateKeyByNewBiometricEnrollment: boolean) => Promise<boolean>,
  storePin: (keyName: string, pinValue: string) => Promise<boolean>,
  retrievePin: (keyName: string) => Promise<string>,
}

export type {
  ConfirmDeviceCredentialsModule
}
