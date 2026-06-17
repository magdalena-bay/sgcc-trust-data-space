create table if not exists data_resource (
    id bigint primary key auto_increment,
    data_id varchar(128) not null unique,
    region varchar(64) not null,
    owner_did varchar(255) not null,
    data_type varchar(64) not null,
    policy_expr text not null,
    policy_org varchar(128) not null,
    policy_role varchar(128) not null,
    policy_grant_status varchar(64) not null,
    cid varchar(255) not null,
    hd_value varchar(128) not null,
    package_hash varchar(128) not null,
    policy_hash varchar(128) not null,
    data_hash varchar(128),
    root varchar(128),
    relay_root varchar(128),
    redis_proof_key varchar(255),
    status varchar(32) not null,
    created_at datetime not null,
    updated_at datetime not null
);

create table if not exists access_audit (
    id bigint primary key auto_increment,
    data_id varchar(128) not null,
    requester_org varchar(128) not null,
    requester_role varchar(128) not null,
    requester_grant_status varchar(64) not null,
    verified tinyint not null,
    granted tinyint not null,
    reason varchar(255),
    created_at datetime not null
);

create table if not exists chain_contract_registry (
    id bigint primary key auto_increment,
    chain_name varchar(64) not null,
    contract_name varchar(128) not null,
    contract_address varchar(255) not null,
    created_at datetime not null
);
