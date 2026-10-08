package edu.mbu.library.modules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialYoutubeTest {

    private static String idOf(String url) {
        Material m = new Material();
        m.setUrl(url);
        return m.getYoutubeId();
    }

    @Test
    void findsTheVideoIdInTheCommonLinkStyles() {
        assertThat(idOf("https://www.youtube.com/watch?v=ukzFI9rgwfU")).isEqualTo("ukzFI9rgwfU");
        assertThat(idOf("https://youtu.be/ukzFI9rgwfU?si=abc")).isEqualTo("ukzFI9rgwfU");
        assertThat(idOf("https://www.youtube.com/embed/ukzFI9rgwfU")).isEqualTo("ukzFI9rgwfU");
        assertThat(idOf("https://www.youtube.com/shorts/ukzFI9rgwfU")).isEqualTo("ukzFI9rgwfU");
        assertThat(idOf("https://www.youtube.com/watch?list=PL1&v=ukzFI9rgwfU")).isEqualTo("ukzFI9rgwfU");
    }

    @Test
    void returnsNullWhenThereIsNoVideoId() {
        assertThat(idOf("https://www.youtube.com/")).isNull();
        assertThat(idOf("https://example.com/page")).isNull();
        assertThat(idOf(null)).isNull();
    }
}
