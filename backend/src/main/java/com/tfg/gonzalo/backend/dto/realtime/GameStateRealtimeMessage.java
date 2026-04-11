package com.tfg.gonzalo.backend.dto.realtime;

import com.tfg.gonzalo.backend.dto.gamestate.GameStateSnapshotResponse;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GameStateRealtimeMessage {

        private String operation;
        private GameStateSnapshotResponse snapshot;
        private List<String> notifications;
        private boolean eventFinished;
        private Instant emittedAt;
}
