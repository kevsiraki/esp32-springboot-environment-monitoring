package temperature.controller;

import temperature.model.*;
import temperature.startup.*;
import temperature.repository.*;
import temperature.exception.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.hateoas.Link;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.Instant;

import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
public class DeviceController {

    private final DeviceRepository deviceRepository;

    DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @ApiOperation("Add a new device")
    @PostMapping("/devices")
    Device newDevice(@RequestBody Device device) {
        return deviceRepository.save(device);
    }

    @ApiOperation("Get all devices")
    @GetMapping("/devices")
    CollectionModel<EntityModel<Device>> all() {
        List<EntityModel<Device>> devices = deviceRepository.findAll().stream()
                .map(device -> EntityModel.of(device,
                        linkTo(methodOn(DeviceController.class).one(device.getId())).withSelfRel(),
                        linkTo(methodOn(DeviceController.class).all()).withRel("devices")))
                .collect(Collectors.toList());

        return CollectionModel.of(devices, linkTo(methodOn(DeviceController.class).all()).withSelfRel());
    }

    @ApiOperation("Get a device by ID")
    @GetMapping("/devices/{id}")
    EntityModel<Device> one(@ApiParam("Device ID") @PathVariable Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));

        return EntityModel.of(device,
                linkTo(methodOn(DeviceController.class).one(id)).withSelfRel(),
                linkTo(methodOn(DeviceController.class).all()).withRel("devices"));
    }

    @ApiOperation("Update a device")
    @PutMapping("/devices/{id}")
    Device replaceDevice(@RequestBody Device newDevice, @PathVariable Long id) {
        return deviceRepository.findById(id)
                .map(device -> {
                    device.setDeviceName(newDevice.getDeviceName());
                    device.setLocation(newDevice.getLocation());
                    return deviceRepository.save(device);
                })
                .orElseGet(() -> {
                    newDevice.setId(id);
                    return deviceRepository.save(newDevice);
                });
    }

    @ApiOperation("Delete a device by ID")
    @DeleteMapping("/devices/{id}")
    void deleteDevice(@ApiParam("Device ID") @PathVariable Long id) {
        if (deviceRepository.existsById(id)) {
            deviceRepository.deleteById(id);
        } else {
            throw new DeviceNotFoundException(id);
        }
    }
}
