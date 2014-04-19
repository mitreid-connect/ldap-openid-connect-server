package edu.mit.kit.userdetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

import com.google.common.collect.ImmutableSet;

/**
 * Mapps all users to ROLE_USER, additionally adds ROLE_ADMIN to anybody whose username
 * is in the configured admins property.
 * 
 * @author jricher
 *
 */
public class MappedLdapAuthoritiesPopulator implements LdapAuthoritiesPopulator {

	private Set<String> admins = Collections.emptySet();
	
	private static final GrantedAuthority ROLE_USER = new SimpleGrantedAuthority("ROLE_USER");
	private static final GrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
	
	public Set<String> getAdmins() {
		return admins;
	}

	public void setAdmins(Set<String> admins) {
		this.admins = admins;
	}

	@Override
	public Collection<? extends GrantedAuthority> getGrantedAuthorities(DirContextOperations userData, String username) {
		
		if (admins.contains(username)) {
			return ImmutableSet.of(ROLE_ADMIN, ROLE_USER);
		} else {
			return ImmutableSet.of(ROLE_USER);
		}
		
	}

}
