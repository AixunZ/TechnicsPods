# AZ100 colour identification evidence

The supplied Technics Audio Connect APK (`com.panasonic.technicsaudioconnect`, version
5.0.1 / versionCode 104) contains the complete colour path. Jadx output was inspected
from the APK only.

* `com.airoha.libbase.RaceCommand.packet.pana.RaceCmdGetColor` emits RACE command `2`
  (`0x0002`) with no payload and expects response type `0x5B`.
* `com.airoha.libmmi.stage.pana.StageGetColor.parsePayloadAndCheckCompeted` checks the
  response status and reads the colour byte at the response payload's colour position
  (the module's normalized RACE payload is status at offset 0, colour at offset 1).
* `DeviceMmiAiroha4` and `DeviceMmiAiroha5` call `OnGetColor`, map the byte to
  `DeviceMmiConstants.MODEL_COLOR_*`, then persist it with `DataStore.setColor(address,
  Constants.DeviceColor)`.
* The same value is included in `StageGetAllData` item command `2`; the supplied idle
  capture contains item `02 00 02 00 0B`, meaning status `00` and colour code `0B`
  (black).
* `Constants.getDeviceColors`/`DesignResources.getPartsMainModelDesignLRC` select AZ100
  artwork by the persisted colour. AZ100's supported artwork choices are black,
  silver, blue, gold and violet.

The module mirrors this verified path in `PanasonicCommand.GET_DEVICE_COLOR`,
`Az100ReadFrames.getDeviceColor`, `Az100ReadResponses.deviceColor` and
`Az100DeviceColor`. Unknown bytes stay `UNKNOWN`; they never silently select black or
overwrite a previously confirmed colour. Model IDs `0x00A0` through `0x00A3` (AZ100,
AZ100E, AZ100G and AZ100P) are required before issuing the colour query.
