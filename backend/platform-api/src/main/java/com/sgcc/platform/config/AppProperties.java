package com.sgcc.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sgcc")
public class AppProperties {
    private Privacy privacy = new Privacy();
    private Ipfs ipfs = new Ipfs();
    private Postgres postgres = new Postgres();
    private Blockchain blockchain = new Blockchain();

    @Data
    public static class Privacy {
        private String baseUrl;
    }

    @Data
    public static class Ipfs {
        private String apiUrl;
        private String gatewayUrl;
    }

    @Data
    public static class Postgres {
        private String url;
        private String username;
        private String password;
    }

    @Data
    public static class Blockchain {
        private String groupId;
        private String serviceUserName;
        private String servicePrivateKey;
        private String serviceAddress;
        private Chain qingdao = new Chain();
        private Chain weifang = new Chain();
        private Chain relay = new Chain();
    }

    @Data
    public static class Chain {
        private String baseUrl;
    }
}
