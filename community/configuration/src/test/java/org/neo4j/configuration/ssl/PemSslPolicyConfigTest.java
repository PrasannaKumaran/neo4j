/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.configuration.ssl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.string.SecureString;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.TestDirectoryExtension;
import org.neo4j.test.rule.TestDirectory;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith( TestDirectoryExtension.class )
class PemSslPolicyConfigTest
{
    @Inject
    private TestDirectory testDirectory;

    @Test
    void shouldFindPolicyDefaults()
    {
        // given
        String policyName = "XYZ";
        PemSslPolicyConfig policyConfig = PemSslPolicyConfig.group( policyName );

        File homeDir = testDirectory.directory( "home" );
        Config config = Config.newBuilder()
                .set( GraphDatabaseSettings.neo4j_home, homeDir.toPath().toAbsolutePath() )
                .set( policyConfig.base_directory, Path.of( "certificates/XYZ" ) )
                .build();

        // derived defaults
        File privateKey = new File( homeDir, "certificates/XYZ/private.key" );
        File publicCertificate = new File( homeDir, "certificates/XYZ/public.crt" );
        File trustedDir = new File( homeDir, "certificates/XYZ/trusted" );
        File revokedDir = new File( homeDir, "certificates/XYZ/revoked" );

        // when
        File privateKeyFromConfig = config.get( policyConfig.private_key ).toFile();
        File publicCertificateFromConfig = config.get( policyConfig.public_certificate ).toFile();
        File trustedDirFromConfig = config.get( policyConfig.trusted_dir ).toFile();
        File revokedDirFromConfig = config.get( policyConfig.revoked_dir ).toFile();
        SecureString privateKeyPassword = config.get( policyConfig.private_key_password );
        boolean trustAll = config.get( policyConfig.trust_all );
        List<String> tlsVersions = config.get( policyConfig.tls_versions );
        List<String> ciphers = config.get( policyConfig.ciphers );
        ClientAuth clientAuth = config.get( policyConfig.client_auth );

        // then
        assertEquals( privateKey, privateKeyFromConfig );
        assertEquals( publicCertificate, publicCertificateFromConfig );
        assertEquals( trustedDir, trustedDirFromConfig );
        assertEquals( revokedDir, revokedDirFromConfig );
        assertNull( privateKeyPassword );
        assertFalse( trustAll );
        assertEquals( singletonList( "TLSv1.2" ), tlsVersions );
        assertNull( ciphers );
        assertEquals( ClientAuth.REQUIRE, clientAuth );
    }

    @Test
    void shouldFindPolicyOverrides()
    {
        // given
        Config.Builder builder = Config.newBuilder();

        String policyName = "XYZ";
        PemSslPolicyConfig policyConfig = PemSslPolicyConfig.group( policyName );

        File homeDir = testDirectory.directory( "home" );

        builder.set( GraphDatabaseSettings.neo4j_home, homeDir.toPath().toAbsolutePath() );
        builder.set( policyConfig.base_directory, Path.of( "certificates/XYZ" ) );

        File privateKey = testDirectory.directory( "/path/to/my.key" );
        File publicCertificate = testDirectory.directory( "/path/to/my.crt" );
        File trustedDir = testDirectory.directory( "/some/other/path/to/trusted" );
        File revokedDir = testDirectory.directory( "/some/other/path/to/revoked" );

        builder.set( policyConfig.private_key, privateKey.toPath().toAbsolutePath() );
        builder.set( policyConfig.public_certificate, publicCertificate.toPath().toAbsolutePath() );
        builder.set( policyConfig.trusted_dir, trustedDir.toPath().toAbsolutePath() );
        builder.set( policyConfig.revoked_dir, revokedDir.toPath().toAbsolutePath() );

        builder.set( policyConfig.trust_all, true );

        builder.set( policyConfig.private_key_password, new SecureString( "setecastronomy" ) );
        builder.set( policyConfig.tls_versions, List.of( "TLSv1.1", "TLSv1.2" ) );
        builder.set( policyConfig.ciphers, List.of( "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" ) );
        builder.set( policyConfig.client_auth, ClientAuth.OPTIONAL );

        Config config = builder.build();

        // when
        File privateKeyFromConfig = config.get( policyConfig.private_key ).toFile();
        File publicCertificateFromConfig = config.get( policyConfig.public_certificate ).toFile();
        File trustedDirFromConfig = config.get( policyConfig.trusted_dir ).toFile();
        File revokedDirFromConfig = config.get( policyConfig.revoked_dir ).toFile();

        SecureString privateKeyPassword = config.get( policyConfig.private_key_password );
        boolean trustAll = config.get( policyConfig.trust_all );
        List<String> tlsVersions = config.get( policyConfig.tls_versions );
        List<String> ciphers = config.get( policyConfig.ciphers );
        ClientAuth clientAuth = config.get( policyConfig.client_auth );

        // then
        assertEquals( privateKey, privateKeyFromConfig );
        assertEquals( publicCertificate, publicCertificateFromConfig );
        assertEquals( trustedDir, trustedDirFromConfig );
        assertEquals( revokedDir, revokedDirFromConfig );

        assertTrue( trustAll );
        assertEquals( "setecastronomy", privateKeyPassword.getString() );
        assertEquals( asList( "TLSv1.1", "TLSv1.2" ), tlsVersions );
        assertEquals( asList( "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" ), ciphers );
        assertEquals( ClientAuth.OPTIONAL, clientAuth );
    }
}