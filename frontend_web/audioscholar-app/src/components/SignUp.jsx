import React, { useState } from 'react';
// Assuming you are using React Router for navigation
// import { Link, useNavigate } from 'react-router-dom';

// Import the signUp function from your auth service file
import { signUp } from '../services/authService'; // Adjust the path if necessary

const SignUp = () => {
    const [fullName, setFullName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    // const navigate = useNavigate(); // If using react-router-dom v6+

    const handleSignUp = async (e) => {
        e.preventDefault();

        setLoading(true);
        setError(null); // Clear previous errors

        // Gather the data to be sent - ensure keys match backend expectation
        const userData = {
            fullName, // or name, username, etc.
            email,
            password,
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
            setFullName('');
            setEmail('');
            setPassword('');


        } catch (err) {
            console.error('Sign up error:', err);
             // Display the error message received from the service
            setError(err.message || 'An unexpected error occurred.');
             // Optionally handle specific error statuses, e.g., if (err.status === 409) { setError('Email already exists.'); }

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
              <div>
                <label htmlFor="fullname" className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
                <input
                  type="text"
                  id="fullname"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500"
                  placeholder="Enter your full name"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  required
                />
              </div>

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

              {/* Display error message if any */}
              {error && (
                  <p className="text-red-500 text-sm mt-2 text-center">{error}</p>
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