import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';

const UserProfileEdit = () => {
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [initialFirstName, setInitialFirstName] = useState('');
  const [initialLastName, setInitialLastName] = useState('');

  const [avatarFile, setAvatarFile] = useState(null);
  const [avatarPreview, setAvatarPreview] = useState(null);
  const [currentProfileImageUrl, setCurrentProfileImageUrl] = useState(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    const fetchUserData = async () => {
      setLoading(true);
      setError(null);
      setSuccessMessage('');
      const token = localStorage.getItem('AuthToken');

      if (!token) {
        setError('Not authenticated.');
        setLoading(false);
        navigate('/signin');
        return;
      }

      try {
        const response = await axios.get(`${API_BASE_URL}api/users/me`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        const userData = response.data;
        setFirstName(userData.firstName || '');
        setLastName(userData.lastName || '');
        setEmail(userData.email || '');
        setInitialFirstName(userData.firstName || '');
        setInitialLastName(userData.lastName || '');
        setCurrentProfileImageUrl(userData.profileImageUrl);
        console.log("Fetched user data for edit:", userData);
      } catch (err) {
        console.error('Error fetching user profile for edit:', err);
        setError('Failed to load user data. Please try again.');
        if (err.response && (err.response.status === 401 || err.response.status === 403)) {
          localStorage.removeItem('AuthToken');
          localStorage.removeItem('userId');
          navigate('/signin');
        }
      } finally {
        setLoading(false);
      }
    };
    fetchUserData();
  }, [navigate]);

  const handleAvatarChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const allowedTypes = ['image/jpeg', 'image/png', 'image/gif'];
      if (!allowedTypes.includes(file.type)) {
        setError('Invalid file type. Please select a JPG, PNG, or GIF image.');
        setAvatarFile(null);
        setAvatarPreview(null);
        e.target.value = null;
        return;
      }
      const maxSize = 5 * 1024 * 1024;
      if (file.size > maxSize) {
        setError('File is too large. Maximum size is 5MB.');
        setAvatarFile(null);
        setAvatarPreview(null);
        e.target.value = null;
        return;
      }

      setError(null);
      setAvatarFile(file);

      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatarPreview(reader.result);
      };
      reader.readAsDataURL(file);
    }
  };

  useEffect(() => {
    return () => {
      if (avatarPreview) {
        URL.revokeObjectURL(avatarPreview);
      }
    };
  }, [avatarPreview]);

  const handleSaveChanges = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSuccessMessage('');
    const token = localStorage.getItem('AuthToken');

    if (!token) {
      setError('Authentication expired. Please log in again.');
      setSaving(false);
      navigate('/signin');
      return;
    }

    let detailsUpdated = false;
    let passwordChanged = false;
    let errorsOccurred = false;
    let avatarUploaded = false;

    const profileDataChanged =
      firstName !== initialFirstName ||
      lastName !== initialLastName;

    if (profileDataChanged) {
      try {
        console.log('Attempting to update profile details (name only)...');
        const updatePayload = { firstName, lastName };
        await axios.put(`${API_BASE_URL}api/users/me`, updatePayload, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        detailsUpdated = true;
        setInitialFirstName(firstName);
        setInitialLastName(lastName);
        console.log('Profile details updated successfully.');
      } catch (err) {
        console.error('Error updating profile details:', err);
        setError(`Failed to update profile details: ${err.response?.data?.message || err.message}`);
        errorsOccurred = true;
      }
    }

    if (!errorsOccurred && avatarFile) {
      try {
        console.log('Attempting to upload new avatar...');
        const formData = new FormData();
        formData.append('avatar', avatarFile);

        const response = await axios.post(`${API_BASE_URL}api/users/me/avatar`, formData, {
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'multipart/form-data'
          }
        });

        avatarUploaded = true;
        setCurrentProfileImageUrl(response.data.profileImageUrl);
        setAvatarFile(null);
        setAvatarPreview(null);
        document.getElementById('avatarInput').value = null;
        console.log('Avatar uploaded successfully:', response.data);
      } catch (err) {
        console.error('Error uploading avatar:', err);
        setError(`Failed to upload avatar: ${err.response?.data?.message || err.message}`);
        errorsOccurred = true;
      }
    }

    if (newPassword || confirmPassword) {
      if (newPassword !== confirmPassword) {
        setError('New passwords do not match.');
        errorsOccurred = true;
      } else if (newPassword.length < 6) {
        setError('New password must be at least 6 characters long.');
        errorsOccurred = true;
      } else {
        if (!errorsOccurred) {
          try {
            console.log('Attempting to change password...');
            await axios.post(`${API_BASE_URL}api/auth/change-password`, { newPassword }, {
              headers: { 'Authorization': `Bearer ${token}` }
            });
            passwordChanged = true;
            setNewPassword('');
            setConfirmPassword('');
            console.log('Password changed successfully.');
          } catch (err) {
            console.error('Error changing password:', err);
            setError(`Failed to change password: ${err.response?.data?.message || err.message}`);
            errorsOccurred = true;
          }
        }
      }
    }

    setSaving(false);

    if (!errorsOccurred && (detailsUpdated || passwordChanged || avatarUploaded)) {
      let message = [];
      if (detailsUpdated) message.push('Profile details updated.');
      if (passwordChanged) message.push('Password changed.');
      if (avatarUploaded) message.push('Avatar updated.');
      setSuccessMessage(message.join(' ') + ' Successfully!');
    } else if (!errorsOccurred && !detailsUpdated && !passwordChanged && !avatarUploaded) {
      setSuccessMessage('No changes were detected.');
    }

  };

  const handleCancel = () => {
    navigate('/profile');
  };

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading form...</div>;
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-100">
      <title>AudioScholar - Edit Profile</title>
      <Header />

      <main className="flex-grow flex items-center justify-center py-12">
        <div className="container mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-lg mx-auto bg-white p-8 md:p-10 rounded-lg shadow-xl">
            <h1 className="text-3xl font-bold text-gray-800 mb-6">Edit Profile</h1>

            <form onSubmit={handleSaveChanges} className="space-y-5">
              <div className="flex flex-col items-center space-y-4 mb-4">
                <label className="block text-sm font-medium text-gray-700">Profile Picture</label>
                <img
                  src={avatarPreview || currentProfileImageUrl || '/icon-512.png'}
                  alt="Avatar Preview"
                  className="w-32 h-32 rounded-full object-cover border-4 border-gray-200 shadow-md"
                />
                <input
                  type="file"
                  id="avatarInput"
                  accept="image/png, image/jpeg, image/gif"
                  onChange={handleAvatarChange}
                  className="hidden"
                  disabled={saving}
                />
                <label
                  htmlFor="avatarInput"
                  className={`cursor-pointer inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-teal-500 ${saving ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  Change Picture
                </label>
              </div>

              {successMessage && (
                <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative" role="alert">
                  <span className="block sm:inline">{successMessage}</span>
                </div>
              )}

              {error && (
                <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative" role="alert">
                  <strong className="font-bold">Error:</strong>
                  <span className="block sm:inline"> {error}</span>
                </div>
              )}

              <div>
                <label htmlFor="firstName" className="block text-sm font-medium text-gray-700 mb-1">First Name</label>
                <input
                  type="text"
                  id="firstName"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  required
                  disabled={saving}
                />
              </div>

              <div>
                <label htmlFor="lastName" className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
                <input
                  type="text"
                  id="lastName"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  required
                  disabled={saving}
                />
              </div>

              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  id="email"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm bg-gray-100 text-gray-500 cursor-not-allowed"
                  value={email}
                  required
                  readOnly
                  disabled={saving || loading}
                />
              </div>

              <div className="border-t border-gray-200 pt-5 mt-5">
                <h2 className="text-lg font-semibold text-gray-700 mb-3">Change Password</h2>

                <div>
                  <label htmlFor="newPassword" className="block text-sm font-medium text-gray-700 mb-1">New Password</label>
                  <input
                    type="password"
                    id="newPassword"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm"
                    placeholder="Enter new password (min. 6 chars)"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    disabled={saving}
                  />
                </div>

                <div>
                  <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
                  <input
                    type="password"
                    id="confirmPassword"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm"
                    placeholder="Confirm your new password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="flex justify-end gap-4 pt-5 mt-5">
                <button
                  type="button"
                  onClick={handleCancel}
                  disabled={saving}
                  className="px-6 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-400 transition duration-150 ease-in-out disabled:opacity-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="inline-flex items-center px-6 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-[#2D8A8A] hover:bg-[#236b6b] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#2D8A8A] transition-colors duration-200 transform hover:-translate-y-0.5 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {saving ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
};

export default UserProfileEdit; 