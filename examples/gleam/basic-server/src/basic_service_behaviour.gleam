//// Handler contract for smithy.beam.demo.basic#BasicService.
////
//// Plain function type aliases (not an OTP behaviour attribute).

import basic_service_types

pub type HandleGetTypeClosure = fn(basic_service_types.GetTypeClosureInput) -> Result(basic_service_types.GetTypeClosureOutput, String)

pub type HandleListBasicItems = fn(basic_service_types.ListBasicItemsInput) -> Result(basic_service_types.ListBasicItemsOutput, String)
