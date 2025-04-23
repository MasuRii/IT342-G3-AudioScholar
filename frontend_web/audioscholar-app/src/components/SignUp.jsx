import React, { useState } from 'react';
// Assuming you are using React Router for navigation
// import { Link, useNavigate } from 'react-router-dom';

// Import the signUp function from your auth service file
import { signUp } from '../services/authService'; // Adjust the path if necessary

const SignUp = () => {
    // Updated state variables for separate names
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [formError, setFormError] = useState(null); // State for form-specific errors
    const [backendError, setBackendError] = useState(null); // State for errors from the backend API
    // const navigate = useNavigate(); // If using react-router-dom v6+

    const handleSignUp = async (e) => {
        e.preventDefault();

        // Clear previous errors
        setFormError(null);
        setBackendError(null);

        // Basic Frontend Validation - Updated for first and last name
        if (!firstName || !lastName || !email || !password) {
            setFormError('Please fill in all fields.');
            return; // Stop the submission if validation fails
        }

        if (password.length < 8) {
             setFormError('Password must be at least 8 characters long.');
             return;
        }

        // Basic email format check (more robust validation is needed for production)
        if (!/\S+@\S+\.\S+/.test(email)) {
             setFormError('Please enter a valid email address.');
             return;
        }

         // Basic name length checks (based on backend logs)
         if (firstName.length < 3 || firstName.length > 50 || lastName.length < 3 || lastName.length > 50) {
             setFormError('First and last names must be between 3 and 50 characters.');
             return;
         }


        setLoading(true);

        // Gather the data to be sent - ENSURE keys match backend expectation
        // Sending firstName and lastName instead of fullName
        const userData = {
            firstName: firstName, // Key should match backend's expected field name
            lastName: lastName,   // Key should match backend's expected field name
            email: email,
            password: password,
        };

        try {
            // Call the signUp function from the service file
            const data = await signUp(userData);

            console.log('Sign up successful:', data);

            // Handle success (e.g., show message, redirect)
            alert('Sign up successful! You can now sign in.');
            // If using react-router-dom:
            // navigate('/signin');
            // You might also want to clear the form fields:
            setFirstName('');
            setLastName('');
            setEmail('');
            setPassword('');

        } catch (err) {
            console.error('Sign up error:', err);
            // Display the error message received from the service
            setBackendError(err.message || 'An unexpected error occurred during sign up.');

        } finally {
            setLoading(false);
        }
    };

    return (
      <main className="flex-grow py-12">
        <div className="container mx-auto px-4">
          <form onSubmit={handleSignUp} className="max-w-md mx-auto bg-white p-8 rounded-lg shadow-md">
            <h1 className="text-3xl font-bold text-gray-800 mb-6">Create Account</h1>
            <p className="text-gray-600 mb-8">Start your journey to better learning</p>

            <div className="space-y-4">
              {/* Input for First Name */}
              <div>
                <label htmlFor="firstName" className="block text-sm font-medium text-gray-700 mb-1">First Name</label>
                <input
                  type="text"
                  id="firstName"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                  placeholder="Enter your first name"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  required
                />
              </div>

               {/* Input for Last Name */}
              <div>
                <label htmlFor="lastName" className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
                <input
                  type="text"
                  id="lastName"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                  placeholder="Enter your last name"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  required
                />
              </div>

              {/* Input for Email */}
              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  id="email"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                  placeholder="Enter your email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              {/* Input for Password */}
              <div>
                <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                <input
                  type="password"
                  id="password"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                  placeholder="Create a password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>

              {/* Display frontend form validation error if any */}
              {formError && (
                  <p className="text-red-500 text-sm mt-2 text-center">{formError}</p>
              )}

               {/* Display backend API error if any */}
              {backendError && (
                  <p className="text-red-500 text-sm mt-2 text-center">{backendError}</p>
              )}


              <button
                  type="submit"
                  className={`w-full bg-teal-500 text-white py-3 px-4 rounded-lg font-medium transition ${loading ? 'opacity-50 cursor-not-allowed' : 'hover:bg-teal-600'}`}
                  disabled={loading}
              >
                {loading ? 'Creating Account...' : 'Create Account'}
              </button>
            </div>

            <div className="mt-6 text-center">
              <p className="text-sm text-gray-600">
                Already have an account?{' '}
                 {/* Consider using React Router Link here */}
                <a href="/signin" className="text-teal-500 hover:text-teal-600 font-medium">Sign in</a>
                 {/* Example using Link: <Link to="/signin" className="text-teal-500 hover:text-teal-600 font-medium">Sign in</Link> */}
              </p>
            </div>
          </form>
        </div>
      </main>
    );
};

export default SignUp;