package cloud.golem.hosttests

/**
 * Marker trait used only to provide a stable, descriptive `traitClass` name for
 * `golemExports`.
 *
 * The actual exported surface is produced by the plugin-generated Scala shim
 * and the internal shim implementation class referenced from `golemExports`.
 */
private[hosttests] trait HostTestsHarness
