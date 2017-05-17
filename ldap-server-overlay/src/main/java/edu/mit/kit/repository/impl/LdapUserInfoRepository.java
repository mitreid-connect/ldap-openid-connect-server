package edu.mit.kit.repository.impl;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import org.mitre.openid.connect.model.DefaultUserInfo;
import org.mitre.openid.connect.model.UserInfo;
import org.mitre.openid.connect.repository.UserInfoRepository;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Looks up the user information from an LDAP template and maps the results
 * into a UserInfo object. This object is then cached.
 * 
 * @author jricher
 *
 */

// TODO: Make this class more pluggable and configurable

public class LdapUserInfoRepository implements UserInfoRepository {

	private LdapTemplate ldapTemplate;
	
	private String emailSuffix = "@example.com";
	
	public LdapTemplate getLdapTemplate() {
		return ldapTemplate;
	}

	public void setLdapTemplate(LdapTemplate ldapTemplate) {
		this.ldapTemplate = ldapTemplate;
	}

	//
	// This code does the heavy lifting that maps the LDAP attributes into UserInfo attributes
	//
	
	private AttributesMapper attributesMapper = new AttributesMapper() {
		@Override
		public Object mapFromAttributes(Attributes attr) throws NamingException {

			if (attr.get("uid") == null) {
				return null; // we can't go on if there's no UID to look up
			}
			
			UserInfo ui = new DefaultUserInfo();
			
			// save the UID as the preferred username
			ui.setPreferredUsername(attr.get("uid").get().toString());
			
			// for now we use the UID as the subject as well (this should probably be different)
			ui.setSub(attr.get("uid").get().toString());
			
			
			// add in the optional fields
			
			// email address
			if (attr.get("mail") != null) {
				ui.setEmail(attr.get("mail").get().toString());
				// if this domain also provisions email addresses, this should be set to true
				ui.setEmailVerified(false);
			}
			
			// phone number
			if (attr.get("telephoneNumber") != null) {
				ui.setPhoneNumber(attr.get("telephoneNumber").get().toString());
				// if this domain also provisions phone numbers, this should be set to true
				ui.setPhoneNumberVerified(false);
			}
			
			// name structure
			if (attr.get("displayName") != null) {
				ui.setName(attr.get("displayName").get().toString());
			}
			
			if (attr.get("givenName") != null) {
				ui.setGivenName(attr.get("givenName").get().toString());
			}
			
			if (attr.get("sn") != null) {
				ui.setFamilyName(attr.get("sn").get().toString());
			}
			
			if (attr.get("initials") != null) {
				ui.setMiddleName(attr.get("initials").get().toString());
			}

			return ui;
			
		}
	};
	
	// lookup result cache, key from username to userinfo
	private LoadingCache<String, UserInfo> cache;

	private CacheLoader<String, UserInfo> cacheLoader = new CacheLoader<String, UserInfo>() {
		@Override
		public UserInfo load(String username) throws Exception {
			
			Filter find = new EqualsFilter("uid", username);
			List res = ldapTemplate.search("", find.encode(), attributesMapper);
			
			if (res.isEmpty()) {
				// user not found, error
				throw new IllegalArgumentException("User not found: " + username);
			} else if (res.size() == 1) {
				// exactly one user found, return them
				return (UserInfo) res.get(0);
			} else {
				// more than one user found, error
				throw new IllegalArgumentException("User not found: " + username);
			}
			
		}
		
	};
	
	
	public LdapUserInfoRepository() {
		this.cache = CacheBuilder.newBuilder()
					.maximumSize(100)
					.expireAfterAccess(14, TimeUnit.DAYS)
					.build(cacheLoader);
	}
	
	
	@Override
	public UserInfo getByUsername(String username) {
		try {
			return cache.get(username);
		} catch (UncheckedExecutionException ue) {
			return null;
		} catch (ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Strip off the suffix defined in emailSuffix and use the rest as a username
	 */
	/* (non-Javadoc)
	 * @see org.mitre.openid.connect.repository.UserInfoRepository#getByEmailAddress(java.lang.String)
	 */
	@Override
	public UserInfo getByEmailAddress(String email) {
		if (!Strings.isNullOrEmpty(email)) {
			if (email.endsWith(getEmailSuffix())) {
				String username = email.substring(0, email.length() - getEmailSuffix().length());
				return getByUsername(username);
			} else {
				// email doesn't match, end
				return null;
			}
		} else {
			// email was null, end
			return null;
		}
	}

	/**
	 * @return the emailSuffix
	 */
	public String getEmailSuffix() {
		return emailSuffix;
	}

	/**
	 * @param emailSuffix the emailSuffix to set
	 */
	public void setEmailSuffix(String emailSuffix) {
		this.emailSuffix = emailSuffix;
	}

}
