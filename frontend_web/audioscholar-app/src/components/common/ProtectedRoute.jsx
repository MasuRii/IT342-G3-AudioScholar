import React from 'react';
import { Navigate } from 'react-router-dom';

const ProtectedRoute = ({ children }) => {
    const token = localStorage.getItem('AuthToken');

    if (!token) {
        console.log('ProtectedRoute: No token found, redirecting to /signin');
        return <Navigate to="/signin" replace />;
    }

    return children;
};

export default ProtectedRoute; 