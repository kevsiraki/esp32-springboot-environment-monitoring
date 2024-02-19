package temperature.controller;

import temperature.model.*;
import temperature.repository.*;
import temperature.exception.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
public class DeviceController {

    private final DeviceRepository deviceRepository;

    DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @ApiOperation("Get all devices associated with the API key")
    @GetMapping("/devices")
    CollectionModel<EntityModel<Device>> all() {
        String currentUserApiKey = getCurrentUserApiKey();

        List<EntityModel<Device>> devices = deviceRepository.findAll().stream()
                .filter(device -> device.getApiKey().equals(currentUserApiKey))
                .map(device -> EntityModel.of(device,
                        linkTo(methodOn(DeviceController.class).one(device.getId())).withSelfRel(),
                        linkTo(methodOn(DeviceController.class).all()).withRel("devices")))
                .collect(Collectors.toList());

        return CollectionModel.of(devices, linkTo(methodOn(DeviceController.class).all()).withSelfRel());
    }

    @ApiOperation("Get a device by ID associated with the API key")
    @GetMapping("/devices/{id}")
    EntityModel<Device> one(@ApiParam("Device ID") @PathVariable String id) {
        String currentUserApiKey = getCurrentUserApiKey();

        Device device = deviceRepository.findById(id)
                .filter(d -> d.getApiKey().equals(currentUserApiKey))
                .orElseThrow(() -> new DeviceNotFoundException(id));

        return EntityModel.of(device,
                linkTo(methodOn(DeviceController.class).one(id)).withSelfRel(),
                linkTo(methodOn(DeviceController.class).all()).withRel("devices"));
    }

    private String getCurrentUserApiKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User userDetails = (User) authentication.getPrincipal();
            return userDetails.getApiKey();
        } else {
            throw new RuntimeException("Unable to retrieve current user's API key");
        }
    }
}
