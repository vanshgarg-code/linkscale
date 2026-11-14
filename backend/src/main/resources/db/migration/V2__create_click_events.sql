CREATE TABLE click_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links (id) ON DELETE CASCADE,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_click_events_link_id_clicked_at ON click_events (link_id, clicked_at);
