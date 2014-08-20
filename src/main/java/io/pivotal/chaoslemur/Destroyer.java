/*
 * Copyright 2014 Pivotal Software, Inc. All Rights Reserved.
 */

package io.pivotal.chaoslemur;

import io.pivotal.chaoslemur.datadog.DataDog;
import io.pivotal.chaoslemur.infrastructure.DestructionException;
import io.pivotal.chaoslemur.infrastructure.Infrastructure;
import org.atteo.evo.inflector.English;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
@RestController
final class Destroyer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final DataDog dataDog;

    private final FateEngine fateEngine;

    private final Infrastructure infrastructure;

    @Autowired
    Destroyer(DataDog dataDog, Infrastructure infrastructure, @Value("${schedule:0 0/10 * * * *}") String
            schedule, FateEngine fateEngine) {
        this.dataDog = dataDog;
        this.fateEngine = fateEngine;
        this.infrastructure = infrastructure;

        this.logger.info("Destruction schedule: {}", schedule);
    }

    /**
     * Trigger method for destruction of members. This method is invoked on a schedule defined by the cron statement
     * stored in the {@code schedule} configuration property.  By default this schedule is {@code 0 0/10 * * * *}.
     */
    @RequestMapping(method = RequestMethod.POST, value = "/destroy")
    @Scheduled(cron = "${schedule:0 0/10 * * * *}")
    public void destroy() {
        UUID identifier = UUID.randomUUID();
        this.logger.info("{} Beginning run...", identifier);

        List<Member> destroyedMembers = new CopyOnWriteArrayList<>();

        this.infrastructure.getMembers().parallelStream().forEach((member) -> {
            if (this.fateEngine.shouldDie(member)) {
                try {
                    this.logger.debug("{} Destroying: {}", identifier, member);
                    this.infrastructure.destroy(member);
                    this.logger.info("{} Destroyed: {}", identifier, member);
                    destroyedMembers.add(member);
                } catch (DestructionException e) {
                    this.logger.warn("{} Destroy failed: {} ({})", identifier, member, e.getMessage());
                }
            }
        });

        this.dataDog.sendEvent(title(identifier), message(destroyedMembers));
    }

    private String message(List<Member> members) {
        int size = members.size();

        String SPACE = "\u00A0";
        String BULLET = "\u2022";

        String s = "\n";
        s += size + English.plural(" VM", size) + " destroyed:\n";
        s += members.stream().sorted().map((member) -> SPACE + SPACE + BULLET + SPACE + member.getName()).collect
                (Collectors.joining("\n"));

        return s;
    }

    private String title(UUID identifier) {
        return String.format("Chaos Lemur Destruction (%s)", identifier);
    }

}